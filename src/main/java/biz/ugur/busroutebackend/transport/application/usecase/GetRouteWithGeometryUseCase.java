package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.interfaces.rest.transport.dto.request.RouteGeometryRequest;
import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.RouteWithGeometryDTO;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.List;

@Service
@Slf4j
public class GetRouteWithGeometryUseCase implements UseCase<String, Mono<RouteWithGeometryDTO>> {

    private final BusRouteRepository busRouteRepository;
    private final ObjectMapper objectMapper;

    public GetRouteWithGeometryUseCase(BusRouteRepository busRouteRepository, ObjectMapper objectMapper) {
        this.busRouteRepository = busRouteRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<RouteWithGeometryDTO> execute(String routeNumber) {
        log.debug("Getting route with geometry for route: {}", routeNumber);

        return busRouteRepository.findByRouteNumberWithGeometry(routeNumber)
                .flatMap(this::enrichWithStopsAndVehicles)
                .doOnNext(route -> log.info("Route {} geometry retrieved: {} forward points, {} backward points",
                        routeNumber,
                        route.getForwardStopsCount(),
                        route.getBackwardStopsCount()));
    }

    public Flux<RouteWithGeometryDTO> getAllActiveRoutes() {
        log.debug("Getting all active routes with basic info");

        return busRouteRepository.findAllActiveWithBasicInfo()
                .doOnComplete(() -> log.debug("All active routes retrieved"));
    }

    public Flux<RouteStopDTO> getRouteStops(String routeNumber, Integer direction) {
        log.debug("Getting stops for route {} direction {}", routeNumber, direction);

        return busRouteRepository.findRouteStopsOrdered(routeNumber, direction)
                .doOnComplete(() -> log.debug("Route stops retrieved for {} direction {}",
                        routeNumber, direction));
    }

    public Mono<String> updateRouteGeometry(String routeNumber, RouteGeometryRequest request) {
        log.info("Updating geometry for route: {}", routeNumber);

        return busRouteRepository.findByRouteNumber(routeNumber)
                .flatMap(route -> {
                    try {
                        // Конвертируем координаты в RouteGeometry
                        var forwardGeometry = createRouteGeometry(request.getForwardCoordinates());
                        var backwardGeometry = request.getBackwardCoordinates() != null ?
                                createRouteGeometry(request.getBackwardCoordinates()) : null;

                        // Обновляем геометрию маршрута
                        route.updateRouteGeometry(forwardGeometry, backwardGeometry);

                        return busRouteRepository.save(route)
                                .map(savedRoute -> "Route geometry updated successfully");

                    } catch (Exception e) {
                        log.error("Failed to update route geometry for {}: {}", routeNumber, e.getMessage());
                        return Mono.error(new IllegalArgumentException("Invalid geometry data: " + e.getMessage()));
                    }
                })
                .doOnSuccess(result -> log.info("Route {} geometry updated successfully", routeNumber));
    }

    // Приватные методы

    private Mono<RouteWithGeometryDTO> enrichWithStopsAndVehicles(RouteWithGeometryDTO route) {
        // Получаем остановки в обоих направлениях
        Mono<RouteWithGeometryDTO> withForwardStops = getRouteStops(route.getRouteNumber(), 0)
                .collectList()
                .map(stops -> {
                    route.setForwardStops(stops);
                    return route;
                });

        Mono<RouteWithGeometryDTO> withBackwardStops = getRouteStops(route.getRouteNumber(), 1)
                .collectList()
                .map(stops -> {
                    route.setBackwardStops(stops);
                    return route;
                });

        // Получаем статистику по автобусам
        Mono<RouteWithGeometryDTO> withVehicleStats = busRouteRepository.getRouteVehicleStatistics(route.getRouteId())
                .map(stats -> {
                    route.setActiveVehiclesCount(stats.activeVehiclesCount());
                    route.setVehiclesInMotion(stats.vehiclesInMotionCount());
                    return route;
                });

        return Mono.zip(withForwardStops, withBackwardStops, withVehicleStats)
                .map(Tuple2::getT1); // Все данные уже установлены в route
    }

    private RouteGeometry createRouteGeometry(List<Double[]> coordinates) {
        return RouteGeometry.fromCoordinateArrays(coordinates);
    }
}