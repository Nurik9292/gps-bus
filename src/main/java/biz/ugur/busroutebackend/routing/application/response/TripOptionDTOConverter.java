package biz.ugur.busroutebackend.routing.application.response;

import biz.ugur.busroutebackend.place.application.dto.GeocodingResult;
import biz.ugur.busroutebackend.place.domain.services.GeocodingService;
import biz.ugur.busroutebackend.routing.application.dto.RouteSegmentDTO;
import biz.ugur.busroutebackend.routing.application.dto.TripOptionDTO;
import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.infrastructure.services.RealTimeETAService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TripOptionDTOConverter {

    private final GeocodingService geocodingService;
    private final RealTimeETAService realTimeETAService;

    public TripOptionDTOConverter(@Nullable GeocodingService geocodingService,
                                   RealTimeETAService realTimeETAService) {
        this.geocodingService = geocodingService;
        this.realTimeETAService = realTimeETAService;
    }

    public Mono<TripOptionDTO> convertToDTO(TripOption tripOption) {
        List<RouteSegmentDTO> segments = tripOption.getRouteSegments()
                .stream()
                .map(this::convertSegmentToDTO)
                .collect(Collectors.toList());

        Mono<Void> resolveFirst = Mono.empty();
        Mono<Void> resolveLast = Mono.empty();

        if (!segments.isEmpty()) {
            RouteSegmentDTO first = segments.getFirst();
            if (first.getFromLocation() != null && first.getFromLocation().getName() == null) {
                double lat = first.getFromLocation().getLatitude();
                double lon = first.getFromLocation().getLongitude();
                resolveFirst = resolveLocationName(lat, lon)
                        .doOnNext(first.getFromLocation()::setName)
                        .then();
            }
            RouteSegmentDTO last = segments.getLast();
            if (last.getToLocation() != null && last.getToLocation().getName() == null) {
                double lat = last.getToLocation().getLatitude();
                double lon = last.getToLocation().getLongitude();
                resolveLast = resolveLocationName(lat, lon)
                        .doOnNext(last.getToLocation()::setName)
                        .then();
            }
        }

        // Find the first BUS_RIDE segment to look up nearest real-time bus
        Mono<TripOptionDTO.NearestBusDTO> nearestBusMono = findNearestBusForTrip(tripOption);

        return Mono.when(resolveFirst, resolveLast)
                .then(nearestBusMono.defaultIfEmpty(new TripOptionDTO.NearestBusDTO(null, null, null, 0, 0)))
                .map(nearestBus -> {
                    TripOptionDTO dto = buildDTO(tripOption, segments);
                    if (nearestBus.getVehicleId() != null) {
                        dto.setNearestBus(nearestBus);
                    }
                    return dto;
                });
    }

    private Mono<TripOptionDTO.NearestBusDTO> findNearestBusForTrip(TripOption tripOption) {
        // Find the first BUS_RIDE segment — that's where the user boards
        return tripOption.getRouteSegments().stream()
                .filter(s -> s.getType() == SegmentType.BUS_RIDE && s.getRouteNumber() != null)
                .findFirst()
                .map(busSegment -> realTimeETAService.findNearestBus(
                                busSegment.getRouteNumber(),
                                busSegment.getFromLocationName() != null
                                        ? busSegment.getFromLocationName()
                                        : "",
                                busSegment.getFromLocation() != null
                                        ? busSegment.getFromLocation().getLatitudeAsDouble()
                                        : Double.NaN,
                                busSegment.getFromLocation() != null
                                        ? busSegment.getFromLocation().getLongitudeAsDouble()
                                        : Double.NaN)
                        .map(info -> new TripOptionDTO.NearestBusDTO(
                                info.vehicleId(),
                                info.licensePlate(),
                                info.routeNumber(),
                                info.etaMinutes(),
                                info.distanceMeters()))
                        .onErrorResume(e -> Mono.empty())
                )
                .orElse(Mono.empty());
    }

    private TripOptionDTO buildDTO(TripOption tripOption, List<RouteSegmentDTO> segments) {
        TripOptionDTO dto = new TripOptionDTO(
                tripOption.getOptionId(),
                tripOption.getTripType().name().toLowerCase(),
                tripOption.getSummary(),
                tripOption.getTotalTravelMinutes(),
                tripOption.getTotalWalkingMinutes(),
                tripOption.getTransfersCount(),
                segments
        );
        dto.setEstimatedDeparture(tripOption.getEstimatedDeparture());
        dto.setEstimatedArrival(tripOption.getEstimatedArrival());
        return dto;
    }

    private Mono<String> resolveLocationName(double lat, double lon) {
        if (geocodingService == null) {
            return Mono.just(formatCoordinates(lat, lon));
        }
        return geocodingService.reverse(lat, lon)
                .map(result -> formatGeocodingResult(result, lat, lon))
                .defaultIfEmpty(formatCoordinates(lat, lon))
                .onErrorReturn(formatCoordinates(lat, lon));
    }

    private String formatGeocodingResult(GeocodingResult result, double lat, double lon) {
        if (result.street() != null) {
            String addr = result.street();
            if (result.houseNumber() != null) {
                addr += ", " + result.houseNumber();
            }
            return addr;
        }
        if (result.displayName() != null) {
            return result.displayName();
        }
        return formatCoordinates(lat, lon);
    }

    private String formatCoordinates(double lat, double lon) {
        return String.format("%.4f°, %.4f°", lat, lon);
    }

    private RouteSegmentDTO convertSegmentToDTO(RouteSegment segment) {
        RouteSegmentDTO dto = new RouteSegmentDTO(
                segment.getType().name().toLowerCase(),
                segment.getDetailedDescription(),
                segment.getDurationMinutes(),
                segment.getRouteNumber(),
                segment.getInstruction()
        );

        dto.setFromLocation(createLocationPointDTO(segment.getFromLocation(), segment.getFromLocationName()));
        dto.setToLocation(createLocationPointDTO(segment.getToLocation(), segment.getToLocationName()));

        addGeometryIfAvailable(dto, segment);

        return dto;
    }

    private void addGeometryIfAvailable(RouteSegmentDTO dto, RouteSegment segment) {
        if (segment.getType() == SegmentType.BUS_RIDE && segment.getRouteGeometryWkt() != null) {
            RouteSegmentDTO.RouteGeometryDTO geometry =
                    RouteSegmentDTO.RouteGeometryDTO.fromRouteSegment(segment);

            if (geometry != null) {
                dto.setRouteGeometry(geometry);
                dto.setTotalDistanceMeters(segment.getTotalDistanceMeters());
            }
        } else if (segment.getType() == SegmentType.WALKING && segment.getWalkingGeometry() != null) {
            dto.setRouteGeometry(new RouteSegmentDTO.RouteGeometryDTO(
                    segment.getWalkingGeometry(), segment.getTotalDistanceMeters()));
            dto.setTotalDistanceMeters(segment.getTotalDistanceMeters());
        }
    }

    private RouteSegmentDTO.LocationPointDTO createLocationPointDTO(Coordinates coordinates, String name) {
        return new RouteSegmentDTO.LocationPointDTO(
                coordinates.getLatitudeAsDouble(),
                coordinates.getLongitudeAsDouble(),
                name
        );
    }
}
