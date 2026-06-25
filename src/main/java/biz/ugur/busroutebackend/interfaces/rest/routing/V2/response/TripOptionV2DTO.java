package biz.ugur.busroutebackend.interfaces.rest.routing.V2.response;

import biz.ugur.busroutebackend.routing.application.dto.RouteSegmentDTO;
import biz.ugur.busroutebackend.routing.application.dto.TripOptionDTO;

import java.time.LocalDateTime;
import java.util.List;

public record TripOptionV2DTO(
        String optionId,
        String tripType,
        String summary,
        int totalTravelMinutes,
        int totalWalkingMinutes,
        int transfersCount,
        int initialWaitingMinutes,
        LocalDateTime estimatedDeparture,
        LocalDateTime estimatedArrival,
        List<RouteSegmentV2> routeSegments,
        CostEstimateV2 costEstimate,
        NearestBusV2 nearestBus
) {

    public static TripOptionV2DTO fromV1(TripOptionDTO option, int initialWaitingMinutes) {
        List<RouteSegmentV2> segments = option.getRouteSegments() == null ? List.of()
                : option.getRouteSegments().stream().map(RouteSegmentV2::fromV1).toList();

        CostEstimateV2 cost = option.getCostEstimate() == null ? null
                : new CostEstimateV2(
                        option.getCostEstimate().getTotalFareManat(),
                        option.getCostEstimate().getNumberOfFares());

        NearestBusV2 nearestBus = option.getNearestBus() == null ? null
                : NearestBusV2.fromV1(option.getNearestBus());

        return new TripOptionV2DTO(
                option.getOptionId(),
                option.getTripType(),
                option.getSummary(),
                option.getTotalTravelMinutes(),
                option.getTotalWalkingMinutes(),
                option.getTransfersCount(),
                initialWaitingMinutes,
                option.getEstimatedDeparture(),
                option.getEstimatedArrival(),
                segments,
                cost,
                nearestBus);
    }

    public record RouteSegmentV2(
            String type,
            String description,
            int durationMinutes,
            String routeNumber,
            String instruction,
            LocationPointV2 fromLocation,
            LocationPointV2 toLocation,
            RouteGeometryV2 routeGeometry,
            Integer totalDistanceMeters
    ) {
        static RouteSegmentV2 fromV1(RouteSegmentDTO segment) {
            return new RouteSegmentV2(
                    segment.getType(),
                    segment.getDescription(),
                    segment.getDurationMinutes(),
                    segment.getRouteNumber(),
                    segment.getInstruction(),
                    LocationPointV2.fromV1(segment.getFromLocation()),
                    LocationPointV2.fromV1(segment.getToLocation()),
                    RouteGeometryV2.fromV1(segment.getRouteGeometry()),
                    segment.getTotalDistanceMeters());
        }
    }

    public record LocationPointV2(
            Double latitude,
            Double longitude,
            String name,
            String stopId
    ) {
        static LocationPointV2 fromV1(RouteSegmentDTO.LocationPointDTO location) {
            return location == null ? null : new LocationPointV2(
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getName(),
                    location.getStopId());
        }
    }

    public record RouteGeometryV2(
            String type,
            List<List<Double>> coordinates,
            Integer totalDistanceMeters
    ) {
        static RouteGeometryV2 fromV1(RouteSegmentDTO.RouteGeometryDTO geometry) {
            return geometry == null ? null : new RouteGeometryV2(
                    geometry.getType(),
                    geometry.getCoordinates(),
                    geometry.getTotalDistanceMeters());
        }
    }

    public record CostEstimateV2(
            double totalFareManat,
            int numberOfFares
    ) {}

    public record NearestBusV2(
            String vehicleId,
            String licensePlate,
            String routeNumber,
            int etaMinutes,
            int distanceMeters
    ) {
        static NearestBusV2 fromV1(TripOptionDTO.NearestBusDTO bus) {
            return new NearestBusV2(
                    bus.getVehicleId(),
                    bus.getLicensePlate(),
                    bus.getRouteNumber(),
                    bus.getEtaMinutes(),
                    bus.getDistanceMeters());
        }
    }
}
