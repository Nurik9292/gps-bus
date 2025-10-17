package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.geospatial.domain.constants.GeoConstants;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import org.springframework.stereotype.Component;


@Component
public class WalkingTimeCalculator {

    private final ETACalculationService etaCalculationService;

    private static final int MAX_WALKING_TIME_MINUTES = GeoConstants.REASONABLE_WALKING_TIME_MINUTES;

    public WalkingTimeCalculator(ETACalculationService etaCalculationService) {
        this.etaCalculationService = etaCalculationService;
    }

    public int calculateWalkingTime(Coordinates from, Coordinates to) {
        int walkingTime = etaCalculationService.calculateWalkingTimeMinutes(from, to);

        if (walkingTime > MAX_WALKING_TIME_MINUTES) {
            throw new IllegalArgumentException(
                    String.format("Walking time %d minutes exceeds maximum %d minutes",
                            walkingTime, MAX_WALKING_TIME_MINUTES));
        }

        return walkingTime;
    }

    public boolean isWalkingDistanceReasonable(Coordinates from, Coordinates to) {
        try {
            calculateWalkingTime(from, to);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}