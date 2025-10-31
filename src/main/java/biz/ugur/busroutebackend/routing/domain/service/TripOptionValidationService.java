package biz.ugur.busroutebackend.routing.domain.service;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Distance;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TripOptionValidationService {

    private final DistanceCalculationService distanceCalculationService;

    private static final int MAX_TOTAL_TRAVEL_MINUTES = 240;
    private static final double MAX_ORIGIN_DESTINATION_TOLERANCE_METERS = 1000.0;

    public boolean isOptionAcceptable(TripOption option, TripSearchCriteria criteria) {
        if (option.getTransfersCount() > criteria.getMaxTransfers()) {
            return false;
        }

        double maxWalkingMinutes = criteria.getMaxWalkingDistanceMeters() / 66.67;
        if (option.getTotalWalkingMinutes() > maxWalkingMinutes) {
            return false;
        }

        return option.getTotalTravelMinutes() <= MAX_TOTAL_TRAVEL_MINUTES;
    }

    public boolean isValidForTrip(TripOption option, Coordinates origin, Coordinates destination) {
        if (option.getRouteSegments().isEmpty()) {
            return false;
        }

        var firstSegment = option.getRouteSegments().getFirst();
        var lastSegment = option.getRouteSegments().getLast();

        Distance startDistance = distanceCalculationService.calculateDistance(
                firstSegment.getFromLocation().getLatitudeAsDouble(),
                firstSegment.getFromLocation().getLongitudeAsDouble(),
                origin.getLatitudeAsDouble(),
                origin.getLongitudeAsDouble()
        );

        if (startDistance.getMeters() > MAX_ORIGIN_DESTINATION_TOLERANCE_METERS) {
            return false;
        }

        Distance endDistance = distanceCalculationService.calculateDistance(
                lastSegment.getToLocation().getLatitudeAsDouble(),
                lastSegment.getToLocation().getLongitudeAsDouble(),
                destination.getLatitudeAsDouble(),
                destination.getLongitudeAsDouble()
        );

        return endDistance.getMeters() <= MAX_ORIGIN_DESTINATION_TOLERANCE_METERS;
    }
}
