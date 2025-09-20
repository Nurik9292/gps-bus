package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RouteDeduplicationService {

    private final RouteDuplicationDetector detector;

    public RouteDeduplicationService(RouteDuplicationDetector detector) {
        this.detector = detector;
    }


    public List<TripOption> deduplicateRoutes(List<SearchResult> allSearchResults) {
        if (allSearchResults == null || allSearchResults.isEmpty()) {
            log.debug("No search results to deduplicate");
            return Collections.emptyList();
        }

        List<TripOption> allRoutes = collectAllRoutes(allSearchResults);

        if (allRoutes.isEmpty()) {
            log.debug("No routes found in search results");
            return Collections.emptyList();
        }

        List<TripOption> uniqueRoutes = performRouteNumberBasedDeduplication(allRoutes);

        validateAndReportResults(allRoutes, uniqueRoutes);

        return uniqueRoutes;
    }


    private List<TripOption> collectAllRoutes(List<SearchResult> searchResults) {
        List<TripOption> allRoutes = new ArrayList<>();

        for (SearchResult result : searchResults) {
            if (result.isSuccessful() && result.getOptions() != null) {
                List<TripOption> routes = result.getOptions().stream()
                        .filter(Objects::nonNull)
                        .toList();

                allRoutes.addAll(routes);

                log.debug("Collected {} routes from {} search",
                        routes.size(), result.getSearchType());
            }
        }

        return allRoutes;
    }


    private List<TripOption> performRouteNumberBasedDeduplication(List<TripOption> allRoutes) {
        if (allRoutes.size() <= 1) {
            return new ArrayList<>(allRoutes);
        }

        Map<String, List<TripOption>> routeGroups = groupRoutesByKey(allRoutes);

        List<TripOption> uniqueRoutes = new ArrayList<>();
        int totalDuplicatesRemoved = 0;

        for (Map.Entry<String, List<TripOption>> entry : routeGroups.entrySet()) {
            String routeKey = entry.getKey();
            List<TripOption> variants = entry.getValue();


            if (variants.size() == 1) {
                uniqueRoutes.add(variants.getFirst());
            } else {
                TripOption bestRoute = selectBestRoute(variants);
                uniqueRoutes.add(bestRoute);
                totalDuplicatesRemoved += (variants.size() - 1);
            }
        }

        return sortRoutesByQuality(uniqueRoutes);
    }


    private Map<String, List<TripOption>> groupRoutesByKey(List<TripOption> routes) {
        return routes.stream()
                .collect(Collectors.groupingBy(detector::createRouteKey));
    }


    private TripOption selectBestRoute(List<TripOption> variants) {
        return variants.stream()
                .min(detector::compareRouteQuality)
                .orElse(variants.getFirst());
    }

    private List<TripOption> sortRoutesByQuality(List<TripOption> routes) {
        return routes.stream()
                .sorted(detector::compareRouteQuality)
                .collect(Collectors.toList());
    }


    private void validateAndReportResults(List<TripOption> originalRoutes, List<TripOption> finalRoutes) {
        RouteDuplicationDetector.ValidationResult validation =
                detector.validateDeduplicationResults(originalRoutes, finalRoutes);

        if (!validation.isValid()) {
            log.error("CRITICAL: Deduplication validation failed!");
            validation.criticalIssues().forEach(issue -> log.error("  - {}", issue));
        }

        if (validation.hasWarnings()) {
            log.warn("Deduplication warnings:");
            validation.warnings().forEach(warning -> log.warn("  - {}", warning));
        }

        String qualityReport = detector.generateDeduplicationReport(originalRoutes, finalRoutes);
        log.info(qualityReport);

        validateNoDuplicatesInResult(finalRoutes);
    }


    private void validateNoDuplicatesInResult(List<TripOption> routes) {
        Set<String> finalRouteKeys = routes.stream()
                .map(detector::createRouteKey)
                .collect(Collectors.toSet());

        if (finalRouteKeys.size() != routes.size()) {
            log.error("CRITICAL: Still have duplicates in final result! {} unique keys vs {} routes",
                    finalRouteKeys.size(), routes.size());
        } else {
            log.info("✓ Deduplication successful - no duplicates in final result");
        }
    }


}