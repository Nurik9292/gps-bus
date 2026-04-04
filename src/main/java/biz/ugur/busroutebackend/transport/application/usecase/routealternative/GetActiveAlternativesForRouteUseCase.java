package biz.ugur.busroutebackend.transport.application.usecase.routealternative;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseFluxUseCase;
import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDataMapper;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.model.RouteAlternative;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAlternativeRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class GetActiveAlternativesForRouteUseCase extends BaseFluxUseCase<GetActiveAlternativesForRouteUseCase.Request, RouteData> {

    private final RouteAlternativeRepository routeAlternativeRepository;
    private final BusRouteRepository busRouteRepository;
    private final RouteStopsService routeStopsService;
    private final VehicleRepository vehicleRepository;
    private final RouteDataMapper routeDataMapper;

    public GetActiveAlternativesForRouteUseCase(
            RouteAlternativeRepository routeAlternativeRepository,
            BusRouteRepository busRouteRepository,
            RouteStopsService routeStopsService,
            VehicleRepository vehicleRepository,
            RouteDataMapper routeDataMapper,
            CorrelationContextService correlationService,
            EventBus eventBus) {
        super(correlationService, eventBus);
        this.routeAlternativeRepository = routeAlternativeRepository;
        this.busRouteRepository = busRouteRepository;
        this.routeStopsService = routeStopsService;
        this.vehicleRepository = vehicleRepository;
        this.routeDataMapper = routeDataMapper;
    }

    @Override
    protected Flux<RouteData> process(Request request) {
        BusRouteId primaryRouteId = BusRouteId.of(request.primaryRouteId);

        return routeAlternativeRepository.findByPrimaryRouteId(primaryRouteId)
                .collectList()
                .flatMapMany(alternatives -> {
                    List<RouteAlternative> sorted = alternatives.stream()
                            .sorted(Comparator.comparingInt(RouteAlternative::getPriority))
                            .toList();

                    return Flux.fromIterable(sorted)
                            .flatMapSequential(alt -> getActiveRouteData(alt.getAlternativeRouteId()));
                })
                .doOnComplete(() -> log.debug("Fetched active alternatives for route: {}", request.primaryRouteId));
    }

    private Mono<RouteData> getActiveRouteData(BusRouteId routeId) {
        return busRouteRepository.findById(routeId)
                .filter(route -> Boolean.TRUE.equals(route.getIsActive()))
                .flatMap(route -> {
                    String id = route.getId().getValue();

                    Mono<List<RouteStopDTO>> forwardStops = routeStopsService.getForwardStopsDTO(id);
                    Mono<List<RouteStopDTO>> backwardStops = routeStopsService.getBackwardStopsDTO(id);
                    Mono<Long> activeVehiclesCount = vehicleRepository.countActiveVehiclesRouteNumber(route.getRouteNumber())
                            .onErrorResume(error -> {
                                log.warn("Error counting active vehicles for route {}: {}", route.getRouteNumber(), error.getMessage());
                                return Mono.just(0L);
                            });

                    return Mono.zip(forwardStops, backwardStops, activeVehiclesCount)
                            .flatMap(tuple -> routeDataMapper.toRouteDataWithStops(
                                    route,
                                    tuple.getT1(),
                                    tuple.getT2(),
                                    tuple.getT3()
                            ));
                });
    }

    @Override
    protected String getBoundedContext() {
        return "transport";
    }

    public record Request(String primaryRouteId) {}
}
