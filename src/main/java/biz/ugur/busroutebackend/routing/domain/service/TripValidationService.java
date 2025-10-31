package biz.ugur.busroutebackend.routing.domain.service;

import biz.ugur.busroutebackend.geospatial.domain.constants.TurkmenistanBounds;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Distance;
import biz.ugur.busroutebackend.routing.domain.exceptions.InvalidLocationException;
import biz.ugur.busroutebackend.routing.domain.exceptions.TripPlanningException;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
public class TripValidationService {

    private final DistanceCalculationService distanceCalculationService;

    private static final double MIN_TRIP_DISTANCE_METERS = 100.0;
    private static final double MAX_TRIP_DISTANCE_METERS = 100_000.0;

    public void validateTripLocations(Coordinates origin, Coordinates destination) {
        validateLocationWithinBounds(origin, "Origin");
        validateLocationWithinBounds(destination, "Destination");
        validateTripDistance(origin, destination);
    }

    public void validateLocationWithinBounds(Coordinates coordinates, String locationType) {
        if (coordinates == null) {
            throw new InvalidLocationException(locationType, "null coordinates");
        }

        if (!TurkmenistanBounds.isWithinStandardBounds(
                coordinates.getLatitudeAsDouble(),
                coordinates.getLongitudeAsDouble())) {
            throw new TripPlanningException(
                    TripPlanningException.PlanningErrorType.LOCATION_OUT_OF_BOUNDS
            );
        }
    }

    public void validateTripDistance(Coordinates origin, Coordinates destination) {
        Distance distance = distanceCalculationService.calculateDistance(
                origin.getLatitudeAsDouble(),
                origin.getLongitudeAsDouble(),
                destination.getLatitudeAsDouble(),
                destination.getLongitudeAsDouble()
        );

        if (distance.getMeters() < MIN_TRIP_DISTANCE_METERS) {
            throw new TripPlanningException(
                    TripPlanningException.PlanningErrorType.DISTANCE_TOO_SHORT
            );
        }

        if (distance.getMeters() > MAX_TRIP_DISTANCE_METERS) {
            throw new TripPlanningException(
                    TripPlanningException.PlanningErrorType.DISTANCE_TOO_LONG
            );
        }
    }

    public boolean isTripWalkable(Coordinates origin, Coordinates destination, int maxWalkingDistanceMeters) {
        Distance distance = distanceCalculationService.calculateDistance(
                origin.getLatitudeAsDouble(),
                origin.getLongitudeAsDouble(),
                destination.getLatitudeAsDouble(),
                destination.getLongitudeAsDouble()
        );
        return distance.getMeters() <= maxWalkingDistanceMeters;
    }

    public int calculateWalkingTimeMinutes(Coordinates origin, Coordinates destination) {
        Distance distance = distanceCalculationService.calculateDistance(
                origin.getLatitudeAsDouble(),
                origin.getLongitudeAsDouble(),
                destination.getLatitudeAsDouble(),
                destination.getLongitudeAsDouble()
        );
        return (int) Math.ceil(distance.getMeters() / 83.33);
    }
}
