package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RouteDuplicationDetector {



    public boolean areExactDuplicates(TripOption route1, TripOption route2) {

        if (!Objects.equals(route1.getUsedRoutes(), route2.getUsedRoutes())) {
            return false;
        }


        if (route1.getTransfersCount() != route2.getTransfersCount()) {
            return false;
        }


        int timeDiff = Math.abs(route1.getTotalTravelMinutes() - route2.getTotalTravelMinutes());
        if (timeDiff > 2) {
            return false;
        }


        double walkingDiff = Math.abs(
                calculateTotalWalkingDistance(route1) - calculateTotalWalkingDistance(route2)
        );
        if (walkingDiff > 50.0) {
            return false;
        }

        return true;
    }

    public int compareRouteQuality(TripOption route1, TripOption route2) {

        int transferCompare = Integer.compare(route1.getTransfersCount(), route2.getTransfersCount());
        if (transferCompare != 0) return transferCompare;


        double totalWalking1 = calculateTotalWalkingDistance(route1);
        double totalWalking2 = calculateTotalWalkingDistance(route2);
        int walkingCompare = Double.compare(totalWalking1, totalWalking2);
        if (walkingCompare != 0) return walkingCompare;


        int timeCompare = Integer.compare(route1.getTotalTravelMinutes(), route2.getTotalTravelMinutes());
        if (timeCompare != 0) return timeCompare;


        return Double.compare(route2.getComfortScore(), route1.getComfortScore());
    }

    public double calculateTotalWalkingDistance(TripOption route) {
        return route.getRouteSegments().stream()
                .filter(segment -> segment.getType() == SegmentType.WALKING)
                .mapToDouble(segment -> segment.getDurationMinutes() * 83.33)
                .sum();
    }

    public double calculateWalkingDistanceToStart(TripOption route) {
        return route.getRouteSegments().stream()
                .filter(segment -> segment.getType() == SegmentType.WALKING)
                .findFirst()
                .map(segment -> segment.getDurationMinutes() * 83.33)
                .orElse(0.0);
    }

    public double calculateWalkingDistanceFromEnd(TripOption route) {
        List<RouteSegment> walkingSegments = route.getRouteSegments().stream()
                .filter(segment -> segment.getType() == SegmentType.WALKING)
                .toList();

        if (walkingSegments.isEmpty()) {
            return 0.0;
        }


        RouteSegment lastWalking = walkingSegments.getLast();
        return lastWalking.getDurationMinutes() * 83.33;
    }



    public String createRouteKey(TripOption route) {
        String routesKey = String.join(",", route.getUsedRoutes());
        int transferCount = route.getTransfersCount();
        return String.format("%s_%d", routesKey, transferCount);
    }


    public String getStartBusStopName(TripOption route) {
        return route.getRouteSegments().stream()
                .filter(segment -> segment.getType() == SegmentType.BUS_RIDE)
                .findFirst()
                .map(segment -> String.format("%.4f, %.4f",
                        segment.getFromLocation().getLatitudeAsDouble(),
                        segment.getFromLocation().getLongitudeAsDouble()))
                .orElse("Unknown");
    }

    public String getEndBusStopName(TripOption route) {
        return route.getRouteSegments().stream()
                .filter(segment -> segment.getType() == SegmentType.BUS_RIDE)
                .reduce((first, second) -> second)
                .map(segment -> String.format("%.4f, %.4f",
                        segment.getToLocation().getLatitudeAsDouble(),
                        segment.getToLocation().getLongitudeAsDouble()))
                .orElse("Unknown");
    }

    public String shortenStopName(String fullName, int maxLength) {
        if (fullName.length() <= maxLength) {
            return fullName;
        }
        return fullName.substring(0, maxLength - 3) + "...";
    }



    public boolean hasReasonableWalkingDistance(TripOption route) {
        double totalWalking = calculateTotalWalkingDistance(route);
        return totalWalking <= 400;
    }

    public QualityAnalysis analyzeRouteQuality(List<TripOption> routes) {
        if (routes.isEmpty()) {
            return new QualityAnalysis(0, 0.0, 0.0, 0.0, 0);
        }


        double[] walkingDistances = routes.stream()
                .mapToDouble(this::calculateTotalWalkingDistance)
                .toArray();

        double avgWalking = Arrays.stream(walkingDistances).average().orElse(0.0);
        double maxWalking = Arrays.stream(walkingDistances).max().orElse(0.0);
        double minWalking = Arrays.stream(walkingDistances).min().orElse(0.0);

        long longWalkingCount = routes.stream()
                .filter(route -> calculateTotalWalkingDistance(route) > 400)
                .count();

        return new QualityAnalysis(
                routes.size(),
                avgWalking,
                maxWalking,
                minWalking,
                (int) longWalkingCount
        );
    }


    public ValidationResult validateDeduplicationResults(List<TripOption> originalRoutes,
                                                         List<TripOption> deduplicatedRoutes) {
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Set<String> originalRouteNumbers = originalRoutes.stream()
                .flatMap(route -> route.getUsedRoutes().stream())
                .collect(Collectors.toSet());

        Set<String> finalRouteNumbers = deduplicatedRoutes.stream()
                .flatMap(route -> route.getUsedRoutes().stream())
                .collect(Collectors.toSet());

        Set<String> lostRouteNumbers = new HashSet<>(originalRouteNumbers);
        lostRouteNumbers.removeAll(finalRouteNumbers);

        if (!lostRouteNumbers.isEmpty()) {
            warnings.add("Lost route numbers: " + lostRouteNumbers);
        }

        for (int i = 0; i < deduplicatedRoutes.size(); i++) {
            for (int j = i + 1; j < deduplicatedRoutes.size(); j++) {
                if (areExactDuplicates(deduplicatedRoutes.get(i), deduplicatedRoutes.get(j))) {
                    issues.add(String.format("Exact duplicates found: %s vs %s",
                            deduplicatedRoutes.get(i).getOptionId(),
                            deduplicatedRoutes.get(j).getOptionId()));
                }
            }
        }

        QualityAnalysis quality = analyzeRouteQuality(deduplicatedRoutes);
        if (!quality.hasGoodQuality()) {
            warnings.add("Final routes quality is below expected standards");
        }

        double removalRate = (double) (originalRoutes.size() - deduplicatedRoutes.size()) / originalRoutes.size();
        if (removalRate > 0.8) {
            warnings.add(String.format("Very high removal rate: %.1f%% - might be too aggressive", removalRate * 100));
        }

        return new ValidationResult(issues, warnings, quality);
    }


    public String generateDeduplicationReport(List<TripOption> originalRoutes,
                                              List<TripOption> deduplicatedRoutes) {
        if (originalRoutes.isEmpty()) {
            return "No routes to analyze";
        }

        QualityAnalysis originalQuality = analyzeRouteQuality(originalRoutes);
        QualityAnalysis finalQuality = analyzeRouteQuality(deduplicatedRoutes);

        int duplicatesRemoved = originalRoutes.size() - deduplicatedRoutes.size();
        double deduplicationRate = (double) duplicatesRemoved / originalRoutes.size() * 100.0;

        StringBuilder report = new StringBuilder();
        report.append("\n=== DEDUPLICATION QUALITY REPORT ===\n");
        report.append(String.format("Routes: %d → %d (removed %d, %.1f%%)\n",
                originalRoutes.size(), deduplicatedRoutes.size(), duplicatesRemoved, deduplicationRate));
        report.append("\nOriginal Quality: ").append(originalQuality).append("\n");
        report.append("Final Quality: ").append(finalQuality).append("\n");

        double walkingImprovement = originalQuality.averageWalkingDistance() - finalQuality.averageWalkingDistance();
        if (walkingImprovement > 0) {
            report.append(String.format("✓ Average walking improved by %.0fm\n", walkingImprovement));
        }

        if (finalQuality.hasGoodQuality()) {
            report.append("✓ Final routes have good quality\n");
        } else {
            report.append("⚠ Final routes quality could be better\n");
        }

        List<TripOption> top3 = deduplicatedRoutes.stream()
                .sorted(this::compareRouteQuality)
                .limit(3)
                .toList();

        report.append("\nTop 3 routes by quality:\n");
        for (int i = 0; i < top3.size(); i++) {
            TripOption route = top3.get(i);
            double totalWalking = calculateTotalWalkingDistance(route);
            report.append(String.format("  %d. Route %s: %dmin, %.0fm walking, %d transfers\n",
                    i + 1, route.getUsedRoutes(), route.getTotalTravelMinutes(),
                    totalWalking, route.getTransfersCount()));
        }

        return report.toString();
    }


    public record QualityAnalysis(
            int totalRoutes,
            double averageWalkingDistance,
            double maxWalkingDistance,
            double minWalkingDistance,
            int longWalkingRoutesCount
    ) {
        public boolean hasGoodQuality() {
            return averageWalkingDistance <= 300 &&
                    maxWalkingDistance <= 500 &&
                    longWalkingRoutesCount <= (totalRoutes * 0.2);
        }

        @Override
        public String toString() {
            return String.format(
                    "QualityAnalysis{total: %d, avgWalk: %.0fm, maxWalk: %.0fm, minWalk: %.0fm, " +
                            "longWalkCount: %d, hasGoodQuality: %s}",
                    totalRoutes, averageWalkingDistance, maxWalkingDistance, minWalkingDistance,
                    longWalkingRoutesCount, hasGoodQuality()
            );
        }
    }


    public record ValidationResult(
            List<String> criticalIssues,
            List<String> warnings,
            QualityAnalysis qualityAnalysis
    ) {
        public boolean isValid() {
            return criticalIssues.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append("ValidationResult{\n");

            if (!criticalIssues.isEmpty()) {
                result.append("  CRITICAL ISSUES:\n");
                criticalIssues.forEach(issue -> result.append("    - ").append(issue).append("\n"));
            }

            if (!warnings.isEmpty()) {
                result.append("  WARNINGS:\n");
                warnings.forEach(warning -> result.append("    - ").append(warning).append("\n"));
            }

            if (criticalIssues.isEmpty() && warnings.isEmpty()) {
                result.append("  ✓ All checks passed\n");
            }

            result.append("  Quality: ").append(qualityAnalysis).append("\n");
            result.append("}");

            return result.toString();
        }
    }
}