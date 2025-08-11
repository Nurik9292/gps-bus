package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.GetAllRoutePaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteList;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteResult;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class GetAllBusRoutesUseCase implements UseCase<Mono<GetAllRoutePaginationQuery>, Mono<RouteList>> {

    private final BusRouteRepository busRouteRepository;
    private final CorrelationContextService correlationService;
    private final RouteStopsService routeStopsService;
    private final VehicleRepository vehicleRepository;

    public GetAllBusRoutesUseCase(BusRouteRepository busRouteRepository,
                                  CorrelationContextService correlationService,
                                  RouteStopsService routeStopsService,
                                  VehicleRepository vehicleRepository) {
        this.busRouteRepository = busRouteRepository;
        this.correlationService = correlationService;
        this.routeStopsService = routeStopsService;
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public Mono<RouteList> execute(Mono<GetAllRoutePaginationQuery> query) {
        return correlationService.executeWithCorrelation(
                query.flatMap(this::executeWithCorrelation),
                "admin"
        );
    }

    private Mono<RouteList> executeWithCorrelation(GetAllRoutePaginationQuery query) {
        Pageable pageable = createPageable(query);

        return correlationService.getCurrentCorrelationId()
                .doOnNext(correlationId -> log.debug(
                        "Fetching bus routes | correlationId={}, page={}, size={}, sortField={}, sortOrder={}, active={}",
                        correlationId, query.page(), query.size(), query.sortField(), query.sortOrder(), query.isActivate()
                ))
                .then(
                        Mono.zip(
                                busRouteRepository.getRoutesWithPagination(pageable).collectList(),
                                busRouteRepository.countActiveRoutes()
                        )
                )
                .flatMap(tuple -> {
                    List<BusRoute> busRoutes = tuple.getT1();
                    Long totalCount = tuple.getT2();

                    return Flux.fromIterable(busRoutes)
                            .flatMap(this::enrichRouteWithStops)
                            .collectList()
                            .map(routeResults -> new RouteList(routeResults, totalCount));
                });
    }

    private Mono<RouteResult> enrichRouteWithStops(BusRoute route) {
        String routeId = route.getId().getValue();

        Mono<List<RouteStopDTO>> forwardStops = routeStopsService.getForwardStopsDTO(routeId);
        Mono<List<RouteStopDTO>> backwardStops = routeStopsService.getBackwardStopsDTO(routeId);
        Mono<Long> activeVehiclesCount = getActiveVehiclesCount(route.getRouteNumber());

        return Mono.zip(forwardStops, backwardStops, activeVehiclesCount)
                .map(tuple -> RouteResult.fromDomainWithStops(
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

    private Pageable createPageable(GetAllRoutePaginationQuery query) {
        Sort sort = Sort.by(
                query.sortOrder().equalsIgnoreCase("desc") ?
                        Sort.Direction.DESC : Sort.Direction.ASC,
                query.sortField()
        );
        return PageRequest.of(query.page() - 1, query.size(), sort);
    }
}
