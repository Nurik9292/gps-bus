package biz.ugur.busroutebackend.routing.infrastructure.services.query;

import biz.ugur.busroutebackend.routing.domain.repository.RouteSearchRepository;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.TwoTransferRouteResult;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TwoTransferRouteQueryService {

    private final RouteSearchRepository routeSearchRepository;

    public Flux<TwoTransferRouteResult> findRoutesWithTwoTransfers(
            List<BusStop> fromStops,
            List<BusStop> toStops,
            double maxTransferDistanceKm) {

        long startTime = System.currentTimeMillis();

        log.debug("🔍 Two-transfer route search: {} origin stops → {} destination stops (max transfer: {}km)",
                fromStops.size(), toStops.size(), maxTransferDistanceKm);

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            log.warn("❌ Empty stop lists provided");
            return Flux.empty();
        }

        return routeSearchRepository.findTwoTransferRoutes(fromStops, toStops, maxTransferDistanceKm)
                .doOnNext(result -> log.debug("✅ Found two-transfer route: {}-{}-{} ({} + {} + {} minutes)",
                        result.firstRoute().getRouteNumber(),
                        result.secondRoute().getRouteNumber(),
                        result.thirdRoute().getRouteNumber(),
                        result.firstRouteTravelMinutes(),
                        result.secondRouteTravelMinutes(),
                        result.thirdRouteTravelMinutes()))
                .doOnComplete(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.debug("✅ Two-transfer route search completed in {}ms", duration);
                })
                .doOnError(error -> log.error("❌ Two-transfer route search failed: {}", error.getMessage(), error));
    }
}
