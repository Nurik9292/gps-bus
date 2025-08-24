package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RouteDuplicationDetector {

    private static final double STOP_SIMILARITY_THRESHOLD = 0.8;

    private static final double TIME_SIMILARITY_THRESHOLD = 0.1;

    private static final int WALKING_TIME_TOLERANCE_MINUTES = 5;

    public boolean isDuplicate(TripOption newRoute, List<TripOption> existingRoutes) {
        return existingRoutes.stream()
                .anyMatch(existing -> {
                    boolean isDupe = compareRoutes(newRoute, existing);
                    if (isDupe) {
                        log.debug("Detected duplicate route: {} vs {} (total time: {} vs {})",
                                newRoute.getOptionId(),
                                existing.getOptionId(),
                                newRoute.getTotalTravelMinutes(),
                                existing.getTotalTravelMinutes());
                    }
                    return isDupe;
                });
    }

    private boolean compareRoutes(TripOption route1, TripOption route2) {

        if (areExactlyEqual(route1, route2)) {
            return true;
        }


        if (route1.getTripType() != route2.getTripType()) {
            return false;
        }


        if (route1.getTransfersCount() != route2.getTransfersCount()) {
            return false;
        }


        if (!compareUsedRoutes(route1, route2)) {
            return false;
        }


        if (!compareTravelTime(route1, route2)) {
            return false;
        }


        return compareWalkingTime(route1, route2);
    }

    private boolean areExactlyEqual(TripOption route1, TripOption route2) {
        return Objects.equals(route1.getUsedRoutes(), route2.getUsedRoutes()) &&
                route1.getTransfersCount() == route2.getTransfersCount() &&
                route1.getTotalTravelMinutes() == route2.getTotalTravelMinutes() &&
                route1.getTotalWalkingMinutes() == route2.getTotalWalkingMinutes();
    }

    private boolean compareUsedRoutes(TripOption route1, TripOption route2) {
        List<String> routes1 = route1.getUsedRoutes();
        List<String> routes2 = route2.getUsedRoutes();

        if (routes1.size() != routes2.size()) {
            return false;
        }


        if (routes1.size() == 1) {
            return routes1.getFirst().equals(routes2.getFirst());
        }


        return routes1.equals(routes2);
    }

    private boolean compareTravelTime(TripOption route1, TripOption route2) {
        int time1 = route1.getTotalTravelMinutes();
        int time2 = route2.getTotalTravelMinutes();

        if (time1 == 0 || time2 == 0) {
            return time1 == time2;
        }


        double maxTime = Math.max(time1, time2);
        double timeDifference = Math.abs(time1 - time2) / maxTime;

        boolean similar = timeDifference <= TIME_SIMILARITY_THRESHOLD;

        if (log.isTraceEnabled() && similar) {
            log.trace("Time similarity: {} vs {} minutes (diff: {:.1f}%)",
                    time1, time2, timeDifference * 100);
        }

        return similar;
    }

    private boolean compareWalkingTime(TripOption route1, TripOption route2) {
        int walking1 = route1.getTotalWalkingMinutes();
        int walking2 = route2.getTotalWalkingMinutes();

        int walkingDifference = Math.abs(walking1 - walking2);

        boolean similar = walkingDifference <= WALKING_TIME_TOLERANCE_MINUTES;

        if (log.isTraceEnabled() && similar) {
            log.trace("Walking time similarity: {} vs {} minutes (diff: {} minutes)",
                    walking1, walking2, walkingDifference);
        }

        return similar;
    }

    private List<String> extractRouteNumbers(TripOption route) {
        return route.getRouteSegments().stream()
                .filter(segment -> segment.getType() == SegmentType.BUS_RIDE)
                .map(RouteSegment::getRouteNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public ComparisonStats getComparisonStats(TripOption route1, TripOption route2) {
        List<String> routes1 = route1.getUsedRoutes();
        List<String> routes2 = route2.getUsedRoutes();

        boolean routesMatch = routes1.equals(routes2);

        double timeSimilarity = 1.0;
        if (route1.getTotalTravelMinutes() > 0 && route2.getTotalTravelMinutes() > 0) {
            timeSimilarity = 1.0 - (Math.abs(route1.getTotalTravelMinutes() - route2.getTotalTravelMinutes()) /
                    (double) Math.max(route1.getTotalTravelMinutes(), route2.getTotalTravelMinutes()));
        }

        double walkingSimilarity = 1.0;
        int walkingDiff = Math.abs(route1.getTotalWalkingMinutes() - route2.getTotalWalkingMinutes());
        if (walkingDiff > 0) {
            walkingSimilarity = Math.max(0.0, 1.0 - (walkingDiff / (double) WALKING_TIME_TOLERANCE_MINUTES));
        }

        return new ComparisonStats(
                routesMatch ? 1.0 : 0.0,
                timeSimilarity,
                walkingSimilarity,
                route1.getTransfersCount() == route2.getTransfersCount(),
                routesMatch,
                route1.getTripType() == route2.getTripType()
        );
    }

    public record ComparisonStats(
            double routeSimilarity,
            double timeSimilarity,
            double walkingSimilarity,
            boolean transferCountMatch,
            boolean routeNumbersMatch,
            boolean tripTypeMatch
    ) {
        public boolean isDuplicate() {
            return routeSimilarity >= STOP_SIMILARITY_THRESHOLD &&
                    transferCountMatch &&
                    routeNumbersMatch &&
                    tripTypeMatch &&
                    timeSimilarity >= (1.0 - TIME_SIMILARITY_THRESHOLD);
        }

        @Override
        public String toString() {
            return String.format("ComparisonStats{routes: %.2f, time: %.2f, walking: %.2f, transfers: %s, routes: %s, tripType: %s, isDupe: %s}",
                    routeSimilarity, timeSimilarity, walkingSimilarity,
                    transferCountMatch, routeNumbersMatch, tripTypeMatch, isDuplicate());
        }
    }
}