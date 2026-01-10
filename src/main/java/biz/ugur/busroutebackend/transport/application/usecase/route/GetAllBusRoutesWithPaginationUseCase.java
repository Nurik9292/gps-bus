package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.GetAllRoutePaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteList;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDataMapper;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.specification.BusRouteSpecifications;
import biz.ugur.busroutebackend.shared.application.util.PaginationHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class GetAllBusRoutesWithPaginationUseCase extends BaseUseCase<Mono<GetAllRoutePaginationQuery>, RouteList> {

    private final BusRouteRepository busRouteRepository;
    private final RouteStopsService routeStopsService;
    private final VehicleRepository vehicleRepository;
    private final RouteDataMapper routeDataMapper;

    public GetAllBusRoutesWithPaginationUseCase(BusRouteRepository busRouteRepository,
                                                CorrelationContextService correlationService,
                                                RouteStopsService routeStopsService,
                                                EventBus eventBus,
                                                VehicleRepository vehicleRepository,
                                                RouteDataMapper routeDataMapper) {
        super(correlationService, eventBus);
        this.busRouteRepository = busRouteRepository;
        this.routeStopsService = routeStopsService;
        this.vehicleRepository = vehicleRepository;
        this.routeDataMapper = routeDataMapper;
    }


    @Override
    protected Mono<RouteList> process(Mono<GetAllRoutePaginationQuery> query) {
        return query.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<RouteList> processInternal(GetAllRoutePaginationQuery query) {
        Pageable pageable = createPageable(query);
        boolean hasSearchQuery = query.query() != null && !query.query().isBlank();

        return correlationService.getCurrentCorrelationId()
                .doOnNext(correlationId -> log.debug(
                        "Fetching bus routes | correlationId={}, page={}, size={}, sortField={}, sortOrder={}, active={}, query={}",
                        correlationId, query.page(), query.size(), query.sortField(), query.sortOrder(), query.isActivate(), query.query()
                ))
                .then(
                        Mono.zip(
                                hasSearchQuery
                                        ? fetchRoutesWithRelevance(query.query(), query.isActivate(), pageable)
                                        : fetchRoutesWithSpecification(query, pageable),
                                busRouteRepository.countActiveRoutes(),
                                hasSearchQuery
                                        ? busRouteRepository.countBySearch(query.query(), query.isActivate())
                                        : countRoutesWithSpecification(query)
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
                                    query.page(),
                                    query.size(),
                                    totalCount
                            ))
                            .doOnSuccess(result -> log.debug(
                                    "Retrieved {} routes on page {} of {} ({} active, {} total)",
                                    result.getRoutes().size(),
                                    result.getPagination().getCurrentPage(),
                                    result.getPagination().getTotalPages(),
                                    result.getActiveCount(),
                                    result.getPagination().getTotalItems()
                            ));
                });
    }

    private Mono<List<BusRoute>> fetchRoutesWithRelevance(String query, Boolean isActive, Pageable pageable) {
        return busRouteRepository.searchWithRelevance(query, isActive, pageable).collectList();
    }

    private Mono<List<BusRoute>> fetchRoutesWithSpecification(GetAllRoutePaginationQuery query, Pageable pageable) {
        Specification<BusRoute> specification = buildSpecificationWithoutQuery(query);
        if (specification == null) {
            return busRouteRepository.findAll(pageable).collectList();
        }
        return busRouteRepository.findBySpecification(specification, pageable).collectList();
    }

    private Mono<Long> countRoutesWithSpecification(GetAllRoutePaginationQuery query) {
        Specification<BusRoute> specification = buildSpecificationWithoutQuery(query);
        if (specification == null) {
            return busRouteRepository.count();
        }
        return busRouteRepository.countBySpecification(specification);
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


    private Specification<BusRoute> buildSpecificationWithoutQuery(GetAllRoutePaginationQuery query) {
        Specification<BusRoute> spec = null;

        if (query.isActivate() != null) {
            spec = query.isActivate()
                    ? BusRouteSpecifications.isActive()
                    : BusRouteSpecifications.isInactive();
        }

        if (query.cityId() != null && !query.cityId().isBlank()) {
            Specification<BusRoute> citySpec = BusRouteSpecifications.servesCity(query.cityId());
            spec = (spec == null) ? citySpec : spec.and(citySpec);
        }

        return spec;
    }

    private Pageable createPageable(GetAllRoutePaginationQuery query) {
        return PaginationHelper.createPageable(
                query.page(), query.size(), query.sortField(), query.sortOrder()
        );
    }
}
