package biz.ugur.busroutebackend.shared.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterializedViewRefreshService {

    private final DatabaseClient databaseClient;

    @Scheduled(fixedRate = 300000, initialDelay = 60000)
    public void refreshActiveRoutesSummary() {
        refreshView("mv_active_routes_summary", "Active Routes Summary")
                .subscribe(
                        duration -> log.debug("[MaterializedView] mv_active_routes_summary refreshed in {}ms", duration),
                        error -> log.error("[MaterializedView] Failed to refresh mv_active_routes_summary", error)
                );
    }

    @Scheduled(fixedRate = 900000, initialDelay = 120000)
    public void refreshPopularDirectRoutes() {
        refreshView("mv_popular_direct_routes", "Popular Direct Routes")
                .subscribe(
                        duration -> log.debug("[MaterializedView] mv_popular_direct_routes refreshed in {}ms", duration),
                        error -> log.error("[MaterializedView] Failed to refresh mv_popular_direct_routes", error)
                );
    }

    @Scheduled(fixedRate = 1200000, initialDelay = 180000)
    public void refreshStopConnections() {
        refreshView("mv_stop_connections", "Stop Connections")
                .subscribe(
                        duration -> log.debug("[MaterializedView] mv_stop_connections refreshed in {}ms", duration),
                        error -> log.error("[MaterializedView] Failed to refresh mv_stop_connections", error)
                );
    }

    @Scheduled(fixedRate = 1800000, initialDelay = 240000)
    public void refreshRouteStatistics() {
        refreshView("mv_route_statistics", "Route Statistics")
                .subscribe(
                        duration -> log.debug("[MaterializedView] mv_route_statistics refreshed in {}ms", duration),
                        error -> log.error("[MaterializedView] Failed to refresh mv_route_statistics", error)
                );
    }

    @Scheduled(fixedRate = 600000, initialDelay = 300000) // every 10 min
    public void refreshSearchHourlyStats() {
        refreshView("mv_search_hourly_stats", "Search Hourly Stats")
                .subscribe(
                        duration -> log.debug("[MaterializedView] mv_search_hourly_stats refreshed in {}ms", duration),
                        error -> log.error("[MaterializedView] Failed to refresh mv_search_hourly_stats", error)
                );
    }

    @Scheduled(fixedRate = 1800000, initialDelay = 360000)
    public void refreshSearchHeatmap() {
        refreshView("mv_search_heatmap", "Search Heatmap")
                .subscribe(
                        duration -> log.debug("[MaterializedView] mv_search_heatmap refreshed in {}ms", duration),
                        error -> log.error("[MaterializedView] Failed to refresh mv_search_heatmap", error)
                );
    }

    @Scheduled(fixedRate = 1800000, initialDelay = 420000) // every 30 min
    public void refreshPopularODPairs() {
        refreshView("mv_popular_od_pairs", "Popular OD Pairs")
                .subscribe(
                        duration -> log.debug("[MaterializedView] mv_popular_od_pairs refreshed in {}ms", duration),
                        error -> log.error("[MaterializedView] Failed to refresh mv_popular_od_pairs", error)
                );
    }

    public Mono<Void> refreshAllViews() {
        log.debug("[MaterializedView] Starting refresh of all materialized views");
        LocalDateTime startTime = LocalDateTime.now();

        return databaseClient.sql("SELECT refresh_all_materialized_views()")
                .fetch()
                .rowsUpdated()
                .doOnSuccess(count -> {
                    Duration duration = Duration.between(startTime, LocalDateTime.now());
                    log.debug("[MaterializedView] All views refreshed successfully in {}ms", duration.toMillis());
                })
                .doOnError(error ->
                        log.error("[MaterializedView] Failed to refresh all views", error))
                .then();
    }

    private Mono<Long> refreshView(String viewName, String displayName) {
        LocalDateTime startTime = LocalDateTime.now();

        log.debug("[MaterializedView] Starting refresh of {}", displayName);

        return databaseClient.sql(String.format("REFRESH MATERIALIZED VIEW CONCURRENTLY %s", viewName))
                .fetch()
                .rowsUpdated()
                .map(count -> {
                    Duration duration = Duration.between(startTime, LocalDateTime.now());
                    return duration.toMillis();
                })
                .doOnError(error ->
                        log.error("[MaterializedView] Error refreshing {}: {}", displayName, error.getMessage()))
                .onErrorResume(error -> {
                    log.warn("[MaterializedView] Continuing after error in {}", displayName);
                    return Mono.just(-1L);
                });
    }

    public Mono<LocalDateTime> getLastRefreshTime(String viewName) {
        String sql = String.format(
                "SELECT last_refreshed FROM %s LIMIT 1",
                viewName
        );

        return databaseClient.sql(sql)
                .map((row, metadata) -> row.get("last_refreshed", LocalDateTime.class))
                .one()
                .doOnError(error ->
                        log.error("[MaterializedView] Failed to get last refresh time for {}", viewName, error));
    }

    public Mono<Boolean> checkViewsHealth() {
        LocalDateTime now = LocalDateTime.now();

        return Mono.zip(
                getLastRefreshTime("mv_active_routes_summary"),
                getLastRefreshTime("mv_popular_direct_routes"),
                getLastRefreshTime("mv_stop_connections"),
                getLastRefreshTime("mv_route_statistics"),
                getLastRefreshTime("mv_search_hourly_stats"),
                getLastRefreshTime("mv_search_heatmap"),
                getLastRefreshTime("mv_popular_od_pairs")
        ).map(tuple -> {
            boolean activeRoutesHealthy = Duration.between(tuple.getT1(), now).toMinutes() < 10;
            boolean directRoutesHealthy = Duration.between(tuple.getT2(), now).toMinutes() < 30;
            boolean connectionsHealthy = Duration.between(tuple.getT3(), now).toMinutes() < 40;
            boolean statisticsHealthy = Duration.between(tuple.getT4(), now).toMinutes() < 60;
            boolean hourlyStatsHealthy = Duration.between(tuple.getT5(), now).toMinutes() < 20;
            boolean heatmapHealthy = Duration.between(tuple.getT6(), now).toMinutes() < 60;
            boolean odPairsHealthy = Duration.between(tuple.getT7(), now).toMinutes() < 60;

            boolean allHealthy = activeRoutesHealthy && directRoutesHealthy &&
                    connectionsHealthy && statisticsHealthy &&
                    hourlyStatsHealthy && heatmapHealthy && odPairsHealthy;

            if (!allHealthy) {
                log.warn("[MaterializedView] Health check failed - activeRoutes: {}, directRoutes: {}, connections: {}, statistics: {}, hourlyStats: {}, heatmap: {}, odPairs: {}",
                        activeRoutesHealthy, directRoutesHealthy, connectionsHealthy, statisticsHealthy,
                        hourlyStatsHealthy, heatmapHealthy, odPairsHealthy);
            }

            return allHealthy;
        }).onErrorResume(error -> {
            log.error("[MaterializedView] Health check failed", error);
            return Mono.just(false);
        });
    }
}
