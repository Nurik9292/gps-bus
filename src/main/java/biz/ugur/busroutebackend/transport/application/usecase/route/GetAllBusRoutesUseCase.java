package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteList;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDataMapper;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class GetAllBusRoutesUseCase extends BaseUseCase<Mono<Void>, RouteList> {

    private final BusRouteRepository busRouteRepository;
    private final RouteStopsService routeStopsService;
    private final VehicleRepository vehicleRepository;
    private final RouteDataMapper routeDataMapper;

    public GetAllBusRoutesUseCase(BusRouteRepository busRouteRepository,
                                  CorrelationContextService correlationService,
                                  EventBus eventBus,
                                  RouteStopsService routeStopsService,
                                  VehicleRepository vehicleRepository,
                                  RouteDataMapper routeDataMapper) {
        super(correlationService, eventBus);
        this.busRouteRepository = busRouteRepository;
        this.routeStopsService = routeStopsService;
        this.vehicleRepository = vehicleRepository;
        this.routeDataMapper = routeDataMapper;
    }


    @Override
    protected Mono<RouteList> process(Mono<Void> request) {
        return request.then(Mono.defer(this::processInternal));
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<RouteList> processInternal() {

        return correlationService.getCurrentCorrelationId()
                .doOnNext(correlationId ->
                        log.debug( "Fetching bus routes | correlationId={}", correlationId))
                .then(
                        Mono.zip(
                                busRouteRepository.findActiveRoutes().collectList(),
                                busRouteRepository.countActiveRoutes(),
                                busRouteRepository.count()
                        )
                )
                .flatMap(tuple -> {
                    List<BusRoute> busRoutes = tuple.getT1();
                    Long activeCount = tuple.getT2();
                    Long totalCount = tuple.getT3();

                    return Flux.fromIterable(busRoutes)
                            .flatMap(this::enrichRouteWithStops)
                            .collectList()
                            .map(routeResults -> new RouteList(
                                    routeResults,
                                    activeCount,
                                    1,  // current page (no pagination for this endpoint)
                                    routeResults.size(),  // page size (all items)
                                    totalCount  // total items in database
                            ))
                            .doOnSuccess(result -> log.debug(
                                    "Retrieved {} routes ({} active, {} total)",
                                    result.getRoutes().size(),
                                    result.getActiveCount(),
                                    result.getPagination().getTotalItems()
                            ));
                });
    }

    private Mono<RouteData> enrichRouteWithStops(BusRoute route) {
        String routeId = route.getId().getValue();
        Mono<List<RouteStopDTO>> forwardStops = routeStopsService.getForwardStopsDTO(routeId);
        Mono<List<RouteStopDTO>> backwardStops = routeStopsService.getBackwardStopsDTO(routeId);
        Mono<Long> activeVehiclesCount = getActiveVehiclesCount(route.getRouteNumber());

        return Mono.zip(forwardStops, backwardStops, activeVehiclesCount)
                .flatMap(tuple -> routeDataMapper.toRouteDataWithStops(
                        route,
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3()
                ))
                .doOnSuccess(result -> log.trace("Enriched route {} with stops", route.getRouteNumber()))
                .doOnError(error -> log.error("Failed to enrich route {} with stops", route.getRouteNumber(), error));
    }

    private Mono<Long> getActiveVehiclesCount(String routeNumber) {
        return vehicleRepository.countActiveVehiclesRouteNumber(routeNumber)
                .onErrorResume(error -> {
                    log.warn("Error counting active vehicles for route {}: {}", routeNumber, error.getMessage());
                    return Mono.just(0L);
                });
    }

}
