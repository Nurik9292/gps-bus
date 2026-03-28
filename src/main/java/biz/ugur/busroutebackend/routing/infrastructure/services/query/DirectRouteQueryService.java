package biz.ugur.busroutebackend.routing.infrastructure.services.query;

import biz.ugur.busroutebackend.routing.domain.repository.RouteSearchRepository;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.DirectRouteResult;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DirectRouteQueryService {

    private final RouteSearchRepository routeSearchRepository;

    public Flux<DirectRouteResult> findDirectRoutes(List<BusStop> fromStops, List<BusStop> toStops) {
        long startTime = System.currentTimeMillis();

        log.debug("🔍 Direct route search: {} origin stops → {} destination stops",
                fromStops.size(), toStops.size());

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            log.warn("❌ Empty stop lists provided");
            return Flux.empty();
        }

        return routeSearchRepository.findDirectRoutes(fromStops, toStops)
                .doOnNext(result -> log.debug("✅ Found direct route: {} ({} minutes)",
                        result.route().getRouteNumber(),
                        result.estimatedTravelMinutes()))
                .doOnComplete(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.debug("✅ Direct route search completed in {}ms", duration);
                })
                .doOnError(error -> log.error("❌ Direct route search failed: {}", error.getMessage(), error));
    }
}
