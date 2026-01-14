package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.repository.PerformanceLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;


@Repository
@Slf4j
public class R2dbcPerformanceLogRepository implements PerformanceLogRepository {

    private static final long SLOW_QUERY_THRESHOLD_MS = 100;

    @Override
    public Mono<Void> logETAPerformance(String stopId, int routesCount, long calculationTimeMs) {
        if (calculationTimeMs > SLOW_QUERY_THRESHOLD_MS) {
            log.info("Slow ETA calculation: stop={}, routes={}, time={}ms", stopId, routesCount, calculationTimeMs);
        }
        return Mono.empty();
    }
}
