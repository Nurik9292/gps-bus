package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.ResolvedRouteData;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDataMapper;
import biz.ugur.busroutebackend.transport.application.services.RouteResolutionService;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.RouteAlternative;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAlternativeRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.specification.BusRouteSpecifications;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class RouteResolutionServiceImpl implements RouteResolutionService {

    private final BusRouteRepository busRouteRepository;
    private final RouteAlternativeRepository routeAlternativeRepository;
    private final RouteStopsService routeStopsService;
    private final VehicleRepository vehicleRepository;
    private final RouteDataMapper routeDataMapper;

    @Override
    public Mono<ResolvedRouteData> resolveById(String routeId) {
        return busRouteRepository.findById(BusRouteId.of(routeId))
                .flatMap(route -> resolveRoute(route, routeId))
                .doOnSuccess(result -> {
                    if (result != null) {
                        log.debug("Resolved route by ID {}: isAlternative={}", routeId, result.isAlternative());
                    } else {
                        log.debug("No active route or alternative found for ID: {}", routeId);
                    }
                });
    }

    @Override
    public Mono<ResolvedRouteData> resolveByNumber(String routeNumber) {
        return resolveByNumber(routeNumber, null);
    }

    @Override
    public Mono<ResolvedRouteData> resolveByNumber(String routeNumber, String cityId) {
        return (cityId == null || cityId.isBlank()
                        ? busRouteRepository.findPreferredByRouteNumber(routeNumber)
                        : busRouteRepository.findByRouteNumberAndCityId(routeNumber, cityId))
                .flatMap(route -> resolveRoute(route, route.getId().getValue()))
                .doOnSuccess(result -> {
                    if (result != null) {
                        log.debug("Resolved route by number {}: isAlternative={}", routeNumber, result.isAlternative());
                    } else {
                        log.debug("No active route or alternative found for number: {}", routeNumber);
                    }
                });
    }

    @Override
    public Flux<ResolvedRouteData> resolveAllRoutes() {
        return resolveAllRoutes(null);
    }

    @Override
    public Flux<ResolvedRouteData> resolveAllRoutes(String cityId) {
        Flux<BusRoute> routesFlux;
        if (cityId != null && !cityId.isBlank()) {
            routesFlux = busRouteRepository.findBySpecification(BusRouteSpecifications.servesCity(cityId));
        } else {
            routesFlux = busRouteRepository.findAll();
        }

        return routesFlux
                .flatMap(route -> resolveRoute(route, route.getId().getValue()))
                .filter(resolved -> resolved != null && resolved.hasActiveRoute())
                .doOnComplete(() -> log.debug("Completed resolving all routes with alternatives, cityId={}", cityId));
    }

    private Mono<ResolvedRouteData> resolveRoute(BusRoute route, String originalRouteId) {
        if (Boolean.TRUE.equals(route.getIsActive())) {
            return enrichRouteData(route)
                    .map(ResolvedRouteData::primary);
        }

        String originalRouteNumber = route.getRouteNumber();
        return findBestAlternative(originalRouteId)
                .flatMap(this::enrichRouteData)
                .map(alternativeData -> ResolvedRouteData.alternative(
                        alternativeData,
                        originalRouteId,
                        originalRouteNumber
                ))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No active alternative found for inactive route: {} ({})",
                            originalRouteNumber, originalRouteId);
                    return Mono.empty();
                }));
    }


    private Mono<BusRoute> findBestAlternative(String primaryRouteId) {
        return routeAlternativeRepository.findByPrimaryRouteId(BusRouteId.of(primaryRouteId))
                .collectList()
                .flatMapMany(alternatives -> {
                    List<RouteAlternative> sorted = alternatives.stream()
                            .sorted(Comparator.comparingInt(RouteAlternative::getPriority))
                            .toList();
                    return Flux.fromIterable(sorted);
                })
                .flatMapSequential(alt ->
                        busRouteRepository.findById(alt.getAlternativeRouteId())
                                .filter(route -> Boolean.TRUE.equals(route.getIsActive()))
                )
                .next();
    }


    private Mono<RouteData> enrichRouteData(BusRoute route) {
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
                ));
    }

    private Mono<Long> getActiveVehiclesCount(String routeNumber) {
        return vehicleRepository.countActiveVehiclesRouteNumber(routeNumber)
                .onErrorResume(error -> {
                    log.warn("Error counting active vehicles for route {}: {}",
                            routeNumber, error.getMessage());
                    return Mono.just(0L);
                });
    }
}
