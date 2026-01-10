package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.repository.PerformanceLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;


@Repository
@Slf4j
public class R2dbcPerformanceLogRepository implements PerformanceLogRepository {

    @Override
    public Mono<Void> logETAPerformance(
            String stopId,
            int routesCount,
            int vehiclesProcessed,
            long calculationTimeMs,
            boolean cacheHit
    ) {
        // Only log slow queries (>100ms) to application log
        if (calculationTimeMs > 100) {
            log.info("Slow ETA calculation: stop={}, routes={}, vehicles={}, time={}ms, cacheHit={}",
                    stopId, routesCount, vehiclesProcessed, calculationTimeMs, cacheHit);
        }
        return Mono.empty();
    }
}
