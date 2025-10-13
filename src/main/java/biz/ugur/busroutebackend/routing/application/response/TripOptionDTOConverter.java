package biz.ugur.busroutebackend.routing.application.response;

import biz.ugur.busroutebackend.routing.application.dto.RouteSegmentDTO;
import biz.ugur.busroutebackend.routing.application.dto.TripOptionDTO;
import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TripOptionDTOConverter {

    public TripOptionDTO convertToDTO(TripOption tripOption) {
        List<RouteSegmentDTO> segments = tripOption.getRouteSegments()
                .stream()
                .map(this::convertSegmentToDTO)
                .collect(Collectors.toList());

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

    private RouteSegmentDTO convertSegmentToDTO(RouteSegment segment) {
        RouteSegmentDTO dto = new RouteSegmentDTO(
                segment.getType().name().toLowerCase(),
                segment.getDetailedDescription(),
                segment.getDurationMinutes(),
                segment.getRouteNumber(),
                segment.getInstruction()
        );

        dto.setFromLocation(createLocationPointDTO(segment.getFromLocation()));
        dto.setToLocation(createLocationPointDTO(segment.getToLocation()));

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
        }
    }

    private RouteSegmentDTO.LocationPointDTO createLocationPointDTO(Coordinates coordinates) {
        return new RouteSegmentDTO.LocationPointDTO(
                coordinates.getLatitudeAsDouble(),
                coordinates.getLongitudeAsDouble(),
                null  // Coordinates doesn't have description
        );
    }
}