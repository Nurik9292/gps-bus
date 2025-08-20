package biz.ugur.busroutebackend.interfaces.rest.transport.controller;

import biz.ugur.busroutebackend.interfaces.rest.transport.dto.request.RouteGeometryRequest;
import biz.ugur.busroutebackend.interfaces.rest.transport.dto.response.RouteGeometryUpdateResponse;
import biz.ugur.busroutebackend.transport.application.dto.*;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.usecase.FindRoutesInAreaUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetRouteWithGeometryUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/routes")
@Slf4j
@CrossOrigin("*")
public class RouteController {

    private final GetRouteWithGeometryUseCase getRouteWithGeometryUseCase;
    private final FindRoutesInAreaUseCase findRoutesInAreaUseCase;

    public RouteController(GetRouteWithGeometryUseCase getRouteWithGeometryUseCase,
                           FindRoutesInAreaUseCase findRoutesInAreaUseCase) {
        this.getRouteWithGeometryUseCase = getRouteWithGeometryUseCase;
        this.findRoutesInAreaUseCase = findRoutesInAreaUseCase;
    }


    @GetMapping("/{routeNumber}/geometry")
    public Mono<ResponseEntity<RouteData>> getRouteGeometry(
            @PathVariable String routeNumber) {

        log.debug("Getting route geometry for route: {}", routeNumber);

        return getRouteWithGeometryUseCase.execute(routeNumber)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnNext(response -> {
                    if (response.getBody() != null) {
                        RouteData route = response.getBody();
                        log.info("Route geometry retrieved for route {}: forward={}km, backward={}km, {} stops",
                                routeNumber, route.totalDistanceForwardKm(),
                                route.totalDistanceBackwardKm(),
                                route.getForwardStopsCount() + route.getBackwardStopsCount());
                    }
                });
    }


    @GetMapping("/in-area")
    public Flux<RouteInAreaDTO> getRoutesInArea(
            @RequestParam @DecimalMin("35.0") @DecimalMax("43.0") Double lat,
            @RequestParam @DecimalMin("52.0") @DecimalMax("67.0") Double lon,
            @RequestParam(defaultValue = "1000") @Min(100) @Max(5000) Integer radius) {

        log.debug("Finding routes in area: lat={}, lon={}, radius={}m", lat, lon, radius);

        return findRoutesInAreaUseCase.execute(new FindRoutesInAreaUseCase.Request(lat, lon, radius))
                .doOnComplete(() -> log.debug("Routes in area search completed for ({}, {})", lat, lon))
                .doOnNext(route -> log.trace("Found route {} at distance {}m",
                        route.getRouteNumber(), Math.round(route.getDistanceToCenterMeters())));
    }


    @GetMapping("/active")
    public Flux<RouteBasicInfoDTO> getActiveRoutes() {
        log.debug("Getting all active routes");

        return getRouteWithGeometryUseCase.getAllActiveRoutes()
                .map(this::toBasicInfo)
                .doOnComplete(() -> log.debug("Active routes list completed"));
    }


    @GetMapping("/{routeNumber}/info")
    public Mono<ResponseEntity<RouteInfoDTO>> getRouteInfo(
            @PathVariable String routeNumber) {

        log.debug("Getting route info for: {}", routeNumber);

        return getRouteWithGeometryUseCase.execute(routeNumber)
                .map(this::toRouteInfo)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnNext(response -> {
                    if (response.getBody() != null) {
                        log.debug("Route info retrieved for: {}", routeNumber);
                    }
                });
    }


    @GetMapping("/{routeNumber}/stops")
    public Flux<RouteStopDTO> getRouteStops(
            @PathVariable String routeNumber,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1) Integer direction) {

        log.debug("Getting stops for route {} in direction {}", routeNumber, direction);

        return getRouteWithGeometryUseCase.getRouteStops(routeNumber, direction)
                .doOnComplete(() -> log.debug("Route stops retrieved for {} direction {}",
                        routeNumber, direction))
                .doOnNext(stop -> log.trace("Stop {}: {} at ({}, {})",
                        stop.getStopSequence(), stop.getStopName(),
                        stop.getLatitude(), stop.getLongitude()));
    }


    @PutMapping("/{routeNumber}/geometry")
    public Mono<ResponseEntity<RouteGeometryUpdateResponse>> updateRouteGeometry(
            @PathVariable String routeNumber,
            @Valid @RequestBody RouteGeometryRequest request) {

        log.info("Updating geometry for route: {} with {} forward points",
                routeNumber, request.getForwardCoordinates().size());

        return getRouteWithGeometryUseCase.updateRouteGeometry(routeNumber, request)
                .map(result -> ResponseEntity.ok(new RouteGeometryUpdateResponse(
                        true, "Route geometry updated successfully", result)))
                .onErrorReturn(ResponseEntity.badRequest().body(new RouteGeometryUpdateResponse(
                        false, "Failed to update route geometry", null)))
                .doOnNext(response -> {
                    if (response.getBody() != null && response.getBody().isSuccess()) {
                        log.info("Route {} geometry updated successfully", routeNumber);
                    } else {
                        log.warn("Failed to update route {} geometry", routeNumber);
                    }
                });
    }


    @GetMapping("/{routeNumber}/statistics")
    public Mono<ResponseEntity<RouteStatisticsDTO>> getRouteStatistics(
            @PathVariable String routeNumber) {

        log.debug("Getting statistics for route: {}", routeNumber);

        return getRouteWithGeometryUseCase.execute(routeNumber)
                .map(this::toRouteStatistics)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnNext(response -> {
                    if (response.getBody() != null) {
                        RouteStatisticsDTO stats = response.getBody();
                        log.debug("Route {} statistics: {} active vehicles, {} in motion",
                                routeNumber, stats.getActiveVehiclesCount(), stats.getVehiclesInMotion());
                    }
                });
    }


    @GetMapping("/search")
    public Flux<RouteSearchResultDTO> searchRoutes(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer limit) {

        log.debug("Searching routes with query: '{}', limit: {}", query, limit);

        return getRouteWithGeometryUseCase.getAllActiveRoutes()
                .map(route -> {
                    RouteSearchResultDTO result = new RouteSearchResultDTO(
                            route.id(),
                            route.routeNumber(),
                            route.routeName(),
                            route.routeColor(),
                            route.activeVehiclesCount()
                    );
                    result.calculateRelevance(query);
                    return result;
                })
                .filter(result -> result.getRelevanceScore() > 0) // Только релевантные результаты
                .sort((r1, r2) -> Double.compare(r2.getRelevanceScore(), r1.getRelevanceScore()))
                .take(limit)
                .doOnComplete(() -> log.debug("Route search completed for query: '{}'", query));
    }


    private RouteBasicInfoDTO toBasicInfo(RouteData route) {
        return new RouteBasicInfoDTO(
                route.routeNumber(),
                route.routeName(),
                route.routeColor(),
                route.totalDistanceForwardKm(),
                route.activeVehiclesCount()
        );
    }

    private RouteInfoDTO toRouteInfo(RouteData route) {
        return new RouteInfoDTO(
                route.id(),
                route.routeNumber(),
                route.routeName(),
                route.routeColor(),
                route.totalDistanceForwardKm(),
                route.totalDistanceBackwardKm(),
                route.activeVehiclesCount(),
                route.getForwardStopsCount(),
                route.getBackwardStopsCount()
        );
    }

    private RouteStatisticsDTO toRouteStatistics(RouteData route) {
        return new RouteStatisticsDTO(
                route.id(),
                route.routeNumber(),
                route.activeVehiclesCount(),
                route.activeVehiclesCount(),
                route.getForwardStopsCount(),
                route.getBackwardStopsCount(),
                route.totalDistanceForwardKm(),
                route.totalDistanceBackwardKm()
        );
    }
}