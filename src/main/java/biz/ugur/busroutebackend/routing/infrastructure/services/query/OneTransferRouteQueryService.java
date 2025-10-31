package biz.ugur.busroutebackend.routing.infrastructure.services.query;

import biz.ugur.busroutebackend.routing.domain.repository.RouteSearchRepository;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.TransferRouteResult;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;


@Service
@Slf4j
@RequiredArgsConstructor
public class OneTransferRouteQueryService {

    private final RouteSearchRepository routeSearchRepository;


    public Flux<TransferRouteResult> findRoutesWithOneTransfer(
            List<BusStop> fromStops,
            List<BusStop> toStops,
            double maxTransferDistanceKm) {

        long startTime = System.currentTimeMillis();

        log.debug("🔍 One-transfer route search: {} origin stops → {} destination stops (max transfer: {}km)",
                fromStops.size(), toStops.size(), maxTransferDistanceKm);

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            log.warn("❌ Empty stop lists provided");
            return Flux.empty();
        }

        return routeSearchRepository.findOneTransferRoutes(fromStops, toStops, maxTransferDistanceKm)
                .doOnNext(result -> log.debug("✅ Found one-transfer route: {}-{} via {} ({} + {} + {} minutes)",
                        result.firstRoute().getRouteNumber(),
                        result.secondRoute().getRouteNumber(),
                        result.transferStop().getStopName(),
                        result.firstRouteTravelMinutes(),
                        result.transferWaitMinutes(),
                        result.secondRouteTravelMinutes()))
                .doOnComplete(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.debug("✅ One-transfer route search completed in {}ms", duration);
                })
                .doOnError(error -> log.error("❌ One-transfer route search failed: {}", error.getMessage(), error));
    }
}
