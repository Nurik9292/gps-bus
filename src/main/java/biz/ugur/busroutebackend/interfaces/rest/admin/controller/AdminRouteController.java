package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.transport.application.dto.BusRouteCreateRequest;
import biz.ugur.busroutebackend.transport.application.dto.BusRouteListResponse;
import biz.ugur.busroutebackend.transport.application.dto.BusRouteResponse;
import biz.ugur.busroutebackend.transport.application.usecase.CreateBusRouteUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.DeleteBusRouteUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.GetAllBusRoutesUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.UpdateBusRouteUseCase;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

    public AdminRouteController(CreateBusRouteUseCase createBusRouteUseCase,
                                GetAllBusRoutesUseCase getAllBusRoutesUseCase,
                                UpdateBusRouteUseCase updateBusRouteUseCase,
                                DeleteBusRouteUseCase deleteBusRouteUseCase) {
        this.createBusRouteUseCase = createBusRouteUseCase;
        this.getAllBusRoutesUseCase = getAllBusRoutesUseCase;
        this.updateBusRouteUseCase = updateBusRouteUseCase;
        this.deleteBusRouteUseCase = deleteBusRouteUseCase;
    }

    @PostMapping
    public Mono<ResponseEntity<BusRouteResponse>> createRoute(@Valid @RequestBody BusRouteCreateRequest request) {
        log.info("Creating bus route: {}", request.getRouteNumber());

        return createBusRouteUseCase.execute(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Bus route created successfully: {}", request.getRouteNumber());
                    }
                })
                .doOnError(error -> log.error("Failed to create bus route: {}", request.getRouteNumber(), error));
    }

    @GetMapping
    public Mono<ResponseEntity<BusRouteListResponse>> getAllRoutes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Boolean active) {

        log.debug("Fetching bus routes (page: {}, size: {}, active: {})", page, size, active);

        return getAllBusRoutesUseCase.execute(new GetAllBusRoutesUseCase.Request(page, size, active))
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> {
                    if (response.getBody() != null) {
                        log.debug("Retrieved {} bus routes", response.getBody().getTotalCount());
                    }
                });
    }

    @PutMapping("/{routeId}")
    public Mono<ResponseEntity<BusRouteResponse>> updateRoute(
            @PathVariable String routeId,
            @Valid @RequestBody BusRouteCreateRequest request) {

        log.info("Updating bus route: {}", routeId);

        return updateBusRouteUseCase.execute(new UpdateBusRouteUseCase.Request(routeId, request))
                .map(ResponseEntity::ok)
                .onErrorReturn(IllegalArgumentException.class,
                        ResponseEntity.notFound().build())
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
}
