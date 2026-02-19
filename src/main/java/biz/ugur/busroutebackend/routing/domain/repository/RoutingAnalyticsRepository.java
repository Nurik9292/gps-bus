package biz.ugur.busroutebackend.routing.domain.repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface RoutingAnalyticsRepository {

    Mono<OverviewStats> getOverviewStats(LocalDateTime from, LocalDateTime to);

    Flux<HourlySearchStats> getHourlySearchStats(LocalDateTime from, LocalDateTime to);

    Flux<HeatmapPoint> getHeatmapData(String pointType);

    Flux<ODPair> getPopularODPairs(int limit);

    Flux<RoutePopularity> getRoutePopularity(int limit, LocalDateTime from, LocalDateTime to);

    Flux<TransferDistribution> getTransferDistribution(LocalDateTime from, LocalDateTime to);

    Flux<DailySearchStats> getDailySearchStats(LocalDateTime from, LocalDateTime to);

    record OverviewStats(
            long totalSearches,
            long successfulSearches,
            long failedSearches,
            double avgOptionsPerSearch,
            double avgDurationMinutes
    ) {}

    record HourlySearchStats(
            LocalDateTime hour,
            int totalSearches,
            int successfulSearches,
            int failedSearches,
            double avgOptions
    ) {}

    record HeatmapPoint(
            double lat,
            double lon,
            int searchCount,
            String pointType
    ) {}

    record ODPair(
            double fromLat,
            double fromLon,
            double toLat,
            double toLon,
            int tripCount,
            double avgOptions
    ) {}

    record RoutePopularity(
            String routeId,
            String routeNumber,
            String routeName,
            long usageCount
    ) {}

    record TransferDistribution(
            int numberOfTransfers,
            long count
    ) {}

    record DailySearchStats(
            LocalDateTime date,
            int totalSearches,
            int successfulSearches,
            int failedSearches
    ) {}
}
