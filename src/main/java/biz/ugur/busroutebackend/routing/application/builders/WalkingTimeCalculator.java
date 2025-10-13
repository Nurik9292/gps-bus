package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.geospatial.domain.constants.GeoConstants;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import org.springframework.stereotype.Component;

/**
 * Calculator for walking time between locations.
 * Now uses centralized GeoConstants for configuration.
 *
 * @since 1.5.0 (Phase 3 - migrated to use GeoConstants)
 */
@Component
public class WalkingTimeCalculator {

    private final ETACalculationService etaCalculationService;

    // Use centralized constant from GeoConstants
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