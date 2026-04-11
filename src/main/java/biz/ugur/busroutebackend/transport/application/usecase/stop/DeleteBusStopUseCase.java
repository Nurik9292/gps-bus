package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Service
@Slf4j
public class DeleteBusStopUseCase extends BaseUseCase<Mono<String>, Void> {

    private final BusStopRepository busStopRepository;
    private final RouteStopRepository routeStopRepository;
    private final BusRouteRepository busRouteRepository;
    private final SecurityContextService securityContextService;

    public DeleteBusStopUseCase(BusStopRepository busStopRepository,
                                RouteStopRepository routeStopRepository,
                                BusRouteRepository busRouteRepository,
                                SecurityContextService securityContextService,
                                CorrelationContextService correlationService,
                                EventBus eventBus) {
        super(correlationService, eventBus);
        this.busStopRepository = busStopRepository;
        this.routeStopRepository = routeStopRepository;
        this.busRouteRepository = busRouteRepository;
        this.securityContextService = securityContextService;
    }

    @Override
    protected Mono<Void> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<Void> processInternal(String stopId) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
          
            return securityContextService.getCurrentUsername()
                .defaultIfEmpty("system")
                .flatMap(username -> {
                    log.info("[DeleteBusStop] CorrelationId: {} - User: {} - Deleting stop: {}",
                            correlationId, username, stopId);

                    return busStopRepository.findById(BusStopId.of(stopId))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Bus stop not found: " + stopId)))
                    .flatMap(busStop -> {
                        log.debug("[DeleteBusStop] Stop found: {}, checking if used in active routes", stopId);

                        return Flux.merge(
                                routeStopRepository.getStopRoutesDetail(stopId, 0),
                                routeStopRepository.getStopRoutesDetail(stopId, 1)
                        )
                        .distinct(stopRouteDetail -> stopRouteDetail.getRouteId())
                        .flatMap(stopRouteDetail -> {
                            return busRouteRepository.findById(BusRouteId.of(stopRouteDetail.getRouteId()))
                                    .filter(route -> Boolean.TRUE.equals(route.getIsActive()))
                                    .map(route -> stopRouteDetail.getRouteNumber());
                        })
                        .collectList()
                        .flatMap(activeRouteNumbers -> {
                            if (!activeRouteNumbers.isEmpty()) {
                                String routeList = String.join(", ", activeRouteNumbers.stream()
                                        .map(Object::toString)
                                        .toList());
                                log.warn("[DeleteBusStop] Cannot delete stop {}: used in {} active routes: {}",
                                        stopId, activeRouteNumbers.size(), routeList);
                                return Mono.error(new IllegalStateException(
                                        String.format("Cannot delete stop %s: it is used in %d active routes (%s). " +
                                                "Please remove the stop from these routes or deactivate them before deletion.",
                                                stopId, activeRouteNumbers.size(), routeList)
                                ));
                            }

                            log.debug("[DeleteBusStop] Stop not used in active routes, proceeding with deletion");
                            return busStopRepository.deleteById(BusStopId.of(stopId));
                        });
                    })
                    .flatMap(v -> {
                        return securityContextService.logAudit("DELETE_STOP", "stop:" + stopId, correlationId.value())
                                .thenReturn(v);
                    })
                    .doOnSuccess(v -> log.info("[DeleteBusStop] Stop deleted successfully: {}", stopId))
                    .doOnError(error -> log.error("[DeleteBusStop] Failed to delete stop {}: {}",
                            stopId, error.getMessage()));
                });
        });
    }
}