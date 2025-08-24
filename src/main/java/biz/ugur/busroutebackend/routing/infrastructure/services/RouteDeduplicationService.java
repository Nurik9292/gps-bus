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

    private final RouteDuplicationDetector duplicationDetector;

    public RouteDeduplicationService(RouteDuplicationDetector duplicationDetector) {
        this.duplicationDetector = duplicationDetector;
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

        List<TripOption> uniqueRoutes = performDeduplication(allRoutes);

        logDeduplicationResults(allSearchResults, allRoutes, uniqueRoutes);

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

    private List<TripOption> performDeduplication(List<TripOption> allRoutes) {
        if (allRoutes.size() <= 1) {
            return new ArrayList<>(allRoutes);
        }

        long startTime = System.currentTimeMillis();

        Map<String, List<TripOption>> routeGroups = groupRoutesByCharacteristics(allRoutes);

        List<TripOption> uniqueRoutes = new ArrayList<>();
        int totalComparisons = 0;
        int duplicatesFound = 0;

        for (Map.Entry<String, List<TripOption>> group : routeGroups.entrySet()) {
            List<TripOption> groupRoutes = group.getValue();

            if (groupRoutes.size() == 1) {
                uniqueRoutes.addAll(groupRoutes);
            } else {
                DeduplicationResult groupResult = deduplicateGroup(groupRoutes);
                uniqueRoutes.addAll(groupResult.uniqueRoutes());
                totalComparisons += groupResult.comparisons();
                duplicatesFound += groupResult.duplicatesRemoved();

                log.debug("Group '{}': {} routes → {} unique (removed {} duplicates)",
                        group.getKey(), groupRoutes.size(), groupResult.uniqueRoutes().size(),
                        groupResult.duplicatesRemoved());
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;

        log.info("Deduplication completed in {}ms: {} total → {} unique (removed {} duplicates, {} comparisons)",
                totalTime, allRoutes.size(), uniqueRoutes.size(), duplicatesFound, totalComparisons);

        return sortRoutesByQuality(uniqueRoutes);
    }

    private Map<String, List<TripOption>> groupRoutesByCharacteristics(List<TripOption> routes) {
        return routes.stream()
                .collect(Collectors.groupingBy(this::createGroupKey));
    }

    private String createGroupKey(TripOption route) {
        int transferCount = route.getTransfersCount();
        int timeGroup = route.getTotalTravelMinutes() / 15;
        String tripType = route.getTripType().name();
        String routesHash = String.join(",", route.getUsedRoutes());

        return String.format("%s_%d_%d_%s", tripType, transferCount, timeGroup, routesHash);
    }

    private DeduplicationResult deduplicateGroup(List<TripOption> groupRoutes) {
        List<TripOption> uniqueRoutes = new ArrayList<>();
        int comparisons = 0;
        int duplicatesRemoved = 0;

        for (TripOption candidate : groupRoutes) {
            boolean isDuplicate = false;

            for (TripOption existing : uniqueRoutes) {
                comparisons++;

                if (duplicationDetector.isDuplicate(candidate, List.of(existing))) {
                    isDuplicate = true;
                    duplicatesRemoved++;

                    TripOption betterRoute = chooseBetterRoute(candidate, existing);
                    if (!betterRoute.equals(existing)) {
                        int index = uniqueRoutes.indexOf(existing);
                        uniqueRoutes.set(index, betterRoute);
                        log.trace("Replaced route with better alternative: {} → {}",
                                existing.getOptionId(), betterRoute.getOptionId());
                    }
                    break;
                }
            }

            if (!isDuplicate) {
                uniqueRoutes.add(candidate);
            }
        }

        return new DeduplicationResult(uniqueRoutes, comparisons, duplicatesRemoved);
    }

    private TripOption chooseBetterRoute(TripOption route1, TripOption route2) {

        int transferCompare = Integer.compare(route1.getTransfersCount(), route2.getTransfersCount());
        if (transferCompare != 0) {
            return transferCompare < 0 ? route1 : route2;
        }

        // 2. Меньше времени поездки = лучше
        int timeCompare = Integer.compare(route1.getTotalTravelMinutes(), route2.getTotalTravelMinutes());
        if (timeCompare != 0) {
            return timeCompare < 0 ? route1 : route2;
        }

        // 3. Меньше пешей ходьбы = лучше
        int walkingCompare = Integer.compare(route1.getTotalWalkingMinutes(), route2.getTotalWalkingMinutes());
        if (walkingCompare != 0) {
            return walkingCompare < 0 ? route1 : route2;
        }

        // 4. Выше comfort score = лучше
        int comfortCompare = Double.compare(route2.getComfortScore(), route1.getComfortScore());
        if (comfortCompare != 0) {
            return comfortCompare < 0 ? route2 : route1;
        }

        // 5. Если всё равно, возвращаем первый
        return route1;
    }

    private List<TripOption> sortRoutesByQuality(List<TripOption> routes) {
        return routes.stream()
                .sorted(this::compareRouteQuality)
                .collect(Collectors.toList());
    }

    private int compareRouteQuality(TripOption route1, TripOption route2) {
        // 1. Меньше пересадок = лучше
        int transferCompare = Integer.compare(route1.getTransfersCount(), route2.getTransfersCount());
        if (transferCompare != 0) return transferCompare;

        // 2. Меньше время поездки = лучше
        int timeCompare = Integer.compare(route1.getTotalTravelMinutes(), route2.getTotalTravelMinutes());
        if (timeCompare != 0) return timeCompare;

        // 3. Меньше пешей ходьбы = лучше
        int walkingCompare = Integer.compare(route1.getTotalWalkingMinutes(), route2.getTotalWalkingMinutes());
        if (walkingCompare != 0) return walkingCompare;

        // 4. Выше comfort score = лучше
        return Double.compare(route2.getComfortScore(), route1.getComfortScore());
    }

    private void logDeduplicationResults(List<SearchResult> searchResults,
                                         List<TripOption> allRoutes,
                                         List<TripOption> uniqueRoutes) {

        Map<String, Long> routesByType = allRoutes.stream()
                .collect(Collectors.groupingBy(
                        route -> route.getTripType().name(),
                        Collectors.counting()
                ));

        Map<String, Long> uniqueByType = uniqueRoutes.stream()
                .collect(Collectors.groupingBy(
                        route -> route.getTripType().name(),
                        Collectors.counting()
                ));

        int duplicatesRemoved = allRoutes.size() - uniqueRoutes.size();
        double deduplicationRate = !allRoutes.isEmpty() ?
                (double) duplicatesRemoved / allRoutes.size() * 100.0 : 0.0;

        log.info("Deduplication summary:");
        log.info("  Total routes before: {} ({})", allRoutes.size(), routesByType);
        log.info("  Unique routes after: {} ({})", uniqueRoutes.size(), uniqueByType);
        log.info("  Duplicates removed: {} ({:.1f}%)", duplicatesRemoved, deduplicationRate);

        // Детальная статистика по типам поиска
        searchResults.forEach(result -> {
            if (result.isSuccessful()) {
                long originalCount = result.getOptions().size();
                String searchType = result.getSearchType();

                log.debug("  {}: {} routes contributed", searchType, originalCount);
            }
        });
    }


    private record DeduplicationResult(
            List<TripOption> uniqueRoutes,
            int comparisons,
            int duplicatesRemoved
    ) {}
}