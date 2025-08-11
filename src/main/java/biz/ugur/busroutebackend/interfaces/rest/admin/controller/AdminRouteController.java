package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.request.route.BusRouteCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.route.BusRouteUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.route.BusRouteListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.route.BusRouteResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.route.CheckRouteNumberResponse;
import biz.ugur.busroutebackend.transport.application.dto.route.GetAllRoutePaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteResult;
import biz.ugur.busroutebackend.transport.application.usecase.route.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/routes")
@Slf4j
@CrossOrigin(origins = "*")
public class AdminRouteController {

    private final CreateBusRouteUseCase createBusRouteUseCase;
    private final GetAllBusRoutesUseCase getAllBusRoutesUseCase;
    private final UpdateBusRouteUseCase updateBusRouteUseCase;
    private final DeleteBusRouteUseCase deleteBusRouteUseCase;
    private final CheckRouteNumberUseCase checkRouteNumberUseCase;

    public AdminRouteController(CreateBusRouteUseCase createBusRouteUseCase,
                                GetAllBusRoutesUseCase getAllBusRoutesUseCase,
                                UpdateBusRouteUseCase updateBusRouteUseCase,
                                DeleteBusRouteUseCase deleteBusRouteUseCase,
                                CheckRouteNumberUseCase checkRouteNumberUseCase) {
        this.createBusRouteUseCase = createBusRouteUseCase;
        this.getAllBusRoutesUseCase = getAllBusRoutesUseCase;
        this.updateBusRouteUseCase = updateBusRouteUseCase;
        this.deleteBusRouteUseCase = deleteBusRouteUseCase;
        this.checkRouteNumberUseCase = checkRouteNumberUseCase;
    }


    @GetMapping
    public Mono<ResponseEntity<BusRouteListResponse>> getAllRoutes(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Boolean active) {

        log.debug("Fetching bus routes (page: {}, size: {}, active: {})", page, size, active);

        return Mono.just(GetAllRoutePaginationQuery.fromParams(page, size, sort, order, active))
                .as(getAllBusRoutesUseCase::execute)
                .map(BusRouteListResponse::fromResult)
                .doOnNext(response -> log.debug("Retrieved {} routes", response.getTotalCount()))
                .map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<BusRouteResponse>> createRoute(@Valid @RequestBody BusRouteCreateRequest request) {
        log.info("Creating bus route: {}", request.getRouteNumber());

        return Mono.just(request.toCommand())
                .as(createBusRouteUseCase::execute)
                .map(this::toRouteResponseEntity)
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Bus route created successfully: {}", request.getRouteNumber());
                    }
                })
                .doOnError(error -> log.error("Failed to create bus route: {}", request.getRouteNumber(), error));


    }


    @PutMapping("/{routeId}")
    public Mono<ResponseEntity<BusRouteResponse>> updateRoute(@PathVariable String routeId,
            @Valid @RequestBody BusRouteUpdateRequest request) {

        log.info("Updating bus route: {}", routeId);
        log.info("Updating bus route2: {}", request);

        return Mono.just(request.toCommand(routeId))
                .as(updateBusRouteUseCase::execute)
                .map(this::toRouteResponseEntity)
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Bus route updated successfully: {}", routeId);
                    }
                })
                .doOnError(error -> log.error("Failed to update bus route: {}", routeId, error));
    }

    @DeleteMapping("/{routeId}")
    public Mono<ResponseEntity<Void>> deleteRoute(@PathVariable String routeId) {
        log.info("Deleting bus route: {}", routeId);

        return deleteBusRouteUseCase.execute(routeId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorReturn(IllegalArgumentException.class,
                        ResponseEntity.notFound().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Bus route deleted successfully: {}", routeId);
                    }
                })
                .doOnError(error -> log.error("Failed to delete bus route: {}", routeId, error));
    }

    @GetMapping("/check-availability")
    public Mono<ResponseEntity<CheckRouteNumberResponse>> checkRouteAvailability(
            @RequestParam(required = false) String routeNumber) {
        return Mono.just(routeNumber)
                .as(checkRouteNumberUseCase::execute)
                .map(CheckRouteNumberResponse::of)
                .map(ResponseEntity::ok);

    }

    private ResponseEntity<BusRouteResponse> toRouteResponseEntity(RouteResult result) {
        return ResponseEntity.ok().body(BusRouteResponse.fromResult(result));
    }

}
