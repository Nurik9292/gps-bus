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
                getLastRefreshTime("mv_route_statistics")
        ).map(tuple -> {
            LocalDateTime activeRoutes = tuple.getT1();
            LocalDateTime directRoutes = tuple.getT2();
            LocalDateTime connections = tuple.getT3();
            LocalDateTime statistics = tuple.getT4();

            boolean activeRoutesHealthy = Duration.between(activeRoutes, now).toMinutes() < 10;

            boolean directRoutesHealthy = Duration.between(directRoutes, now).toMinutes() < 30;

            boolean connectionsHealthy = Duration.between(connections, now).toMinutes() < 40;

            boolean statisticsHealthy = Duration.between(statistics, now).toMinutes() < 60;

            boolean allHealthy = activeRoutesHealthy && directRoutesHealthy &&
                    connectionsHealthy && statisticsHealthy;

            if (!allHealthy) {
                log.warn("[MaterializedView] Health check failed - activeRoutes: {}, directRoutes: {}, connections: {}, statistics: {}",
                        activeRoutesHealthy, directRoutesHealthy, connectionsHealthy, statisticsHealthy);
            }

            return allHealthy;
        }).onErrorResume(error -> {
            log.error("[MaterializedView] Health check failed", error);
            return Mono.just(false);
        });
    }
}
