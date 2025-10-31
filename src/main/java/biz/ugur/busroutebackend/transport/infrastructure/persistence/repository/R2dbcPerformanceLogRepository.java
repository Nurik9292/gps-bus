package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.repository.PerformanceLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;


@Repository
@Slf4j
public class R2dbcPerformanceLogRepository implements PerformanceLogRepository {

    private final DatabaseClient databaseClient;

    public R2dbcPerformanceLogRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> logETAPerformance(
            String stopId,
            int routesCount,
            int vehiclesProcessed,
            long calculationTimeMs,
            boolean cacheHit
    ) {
        if (calculationTimeMs <= 100) {
            return Mono.empty();
        }

        String sql = """
            SELECT log_eta_performance(:stopId, :routesCount, :vehiclesProcessed, :calculationTime, :cacheHit)
            """;

        return databaseClient.sql(sql)
                .bind("stopId", stopId)
                .bind("routesCount", routesCount)
                .bind("vehiclesProcessed", vehiclesProcessed)
                .bind("calculationTime", (int) calculationTimeMs)
                .bind("cacheHit", cacheHit)
                .then()
                .doOnSuccess(result -> log.debug("Logged ETA performance for stop {}: {}ms", stopId, calculationTimeMs))
                .doOnError(error -> log.warn("Failed to log ETA performance: {}", error.getMessage()))
                .onErrorResume(error -> Mono.empty()); // Don't fail the request if logging fails
    }
}
