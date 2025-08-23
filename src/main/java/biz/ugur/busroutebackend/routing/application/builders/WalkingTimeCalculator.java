package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.Location;
import org.springframework.stereotype.Component;

@Component
public class WalkingTimeCalculator {

    private final ETACalculationService etaCalculationService;

    private static final int MAX_WALKING_TIME_MINUTES = 15;

    public WalkingTimeCalculator(ETACalculationService etaCalculationService) {
        this.etaCalculationService = etaCalculationService;
    }

    public int calculateWalkingTime(Location from, Location to) {
        int walkingTime = etaCalculationService.calculateWalkingTimeMinutes(from, to);

        if (walkingTime > MAX_WALKING_TIME_MINUTES) {
            throw new IllegalArgumentException(
                    String.format("Walking time %d minutes exceeds maximum %d minutes",
                            walkingTime, MAX_WALKING_TIME_MINUTES));
        }

        return walkingTime;
    }

    public boolean isWalkingDistanceReasonable(Location from, Location to) {
        try {
            calculateWalkingTime(from, to);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}