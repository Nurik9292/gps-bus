package biz.ugur.busroutebackend.interfaces.rest.transport.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.transport.V1.request.RouteGeometryRequest;
import biz.ugur.busroutebackend.interfaces.rest.transport.V1.response.RouteGeometryUpdateResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.dto.*;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.usecase.FindRoutesInAreaUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetRouteWithGeometryUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.API_V1;

@RestController
@RequestMapping( API_V1 + "/routes")
@CrossOrigin("*")
public class RouteController extends BaseController {

    private final GetRouteWithGeometryUseCase getRouteWithGeometryUseCase;
    private final FindRoutesInAreaUseCase findRoutesInAreaUseCase;

    public RouteController(GetRouteWithGeometryUseCase getRouteWithGeometryUseCase,
                           FindRoutesInAreaUseCase findRoutesInAreaUseCase,
                           MessageSource messageSource) {
        super(messageSource);
        this.getRouteWithGeometryUseCase = getRouteWithGeometryUseCase;
        this.findRoutesInAreaUseCase = findRoutesInAreaUseCase;
    }

    @Override
    protected String getControllerName() {
        return RouteController.class.getSimpleName();
    }


    @GetMapping("/{routeNumber}/geometry")
    public Mono<ResponseEntity<ApiResponse<RouteData>>> getRouteGeometry(
            @PathVariable String routeNumber) {

        return ok(getRouteWithGeometryUseCase.execute(routeNumber));
    }


    @GetMapping("/in-area")
    public Mono<ResponseEntity<ApiResponse<List<RouteInAreaDTO>>>> getRoutesInArea(
            @RequestParam @DecimalMin("35.0") @DecimalMax("43.0") Double lat,
            @RequestParam @DecimalMin("52.0") @DecimalMax("67.0") Double lon,
            @RequestParam(defaultValue = "1000") @Min(100) @Max(5000) Integer radius) {

        return okList(findRoutesInAreaUseCase.execute(new FindRoutesInAreaUseCase.Request(lat, lon, radius)));
    }


    @GetMapping("/active")
    public Mono<ResponseEntity<ApiResponse<List<RouteBasicInfoDTO>>>> getActiveRoutes() {
        return okList(getRouteWithGeometryUseCase.getAllActiveRoutes()
                .map(this::toBasicInfo));
    }


    @GetMapping("/{routeNumber}/info")
    public Mono<ResponseEntity<ApiResponse<RouteInfoDTO>>> getRouteInfo(
            @PathVariable String routeNumber) {

        return ok(getRouteWithGeometryUseCase.execute(routeNumber)
                .map(this::toRouteInfo));
    }


    @GetMapping("/{routeNumber}/stops")
    public Mono<ResponseEntity<ApiResponse<List<RouteStopDTO>>>> getRouteStops(
            @PathVariable String routeNumber,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1) Integer direction) {

        return okList(getRouteWithGeometryUseCase.getRouteStops(routeNumber, direction));
    }


    @PutMapping("/{routeNumber}/geometry")
    public Mono<ResponseEntity<ApiResponse<RouteGeometryUpdateResponse>>> updateRouteGeometry(
            @PathVariable String routeNumber,
            @Valid @RequestBody RouteGeometryRequest request) {

        return ok(getRouteWithGeometryUseCase.updateRouteGeometry(routeNumber, request)
                .map(result -> new RouteGeometryUpdateResponse(
                        true, "Route geometry updated successfully", result)));
    }


    @GetMapping("/{routeNumber}/statistics")
    public Mono<ResponseEntity<ApiResponse<RouteStatisticsDTO>>> getRouteStatistics(
            @PathVariable String routeNumber) {

        return ok(getRouteWithGeometryUseCase.execute(routeNumber)
                .map(this::toRouteStatistics));
    }


    @GetMapping("/search")
    public Mono<ResponseEntity<ApiResponse<List<RouteSearchResultDTO>>>> searchRoutes(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer limit) {

        return okList(getRouteWithGeometryUseCase.getAllActiveRoutes()
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
                .filter(result -> result.getRelevanceScore() > 0)
                .sort((r1, r2) -> Double.compare(r2.getRelevanceScore(), r1.getRelevanceScore()))
                .take(limit));
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