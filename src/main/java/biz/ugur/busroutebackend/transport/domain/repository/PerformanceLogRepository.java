package biz.ugur.busroutebackend.transport.domain.repository;

import reactor.core.publisher.Mono;


public interface PerformanceLogRepository {

    Mono<Void> logETAPerformance(
            String stopId,
            int routesCount,
            int vehiclesProcessed,
            long calculationTimeMs,
            boolean cacheHit
    );
}
