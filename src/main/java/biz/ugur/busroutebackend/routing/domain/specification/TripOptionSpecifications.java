package biz.ugur.busroutebackend.routing.domain.specification;

import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;

import java.util.function.Predicate;

public class TripOptionSpecifications {

    public static Predicate<TripOption> hasAcceptableTransfers(TripSearchCriteria criteria) {
        return option -> option.getTransfersCount() <= criteria.getMaxTransfers();
    }

    public static Predicate<TripOption> hasAcceptableWalkingTime(TripSearchCriteria criteria) {
        double maxWalkingMinutes = criteria.getMaxWalkingDistanceMeters() / 66.67;
        return option -> option.getTotalWalkingMinutes() <= maxWalkingMinutes;
    }

    public static Predicate<TripOption> hasAcceptableTotalTime() {
        return option -> option.getTotalTravelMinutes() <= 240;
    }

    public static Predicate<TripOption> isAccessible() {
        return TripOption::isAccessible;
    }

    public static Predicate<TripOption> isAcceptable(TripSearchCriteria criteria) {
        return hasAcceptableTransfers(criteria)
                .and(hasAcceptableWalkingTime(criteria))
                .and(hasAcceptableTotalTime());
    }

    public static Predicate<TripOption> hasMinimumReliability(double minScore) {
        return option -> option.getReliabilityScore() >= minScore;
    }
}
