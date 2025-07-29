package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.transport.application.dto.BusStopCreateRequest;
import biz.ugur.busroutebackend.transport.application.dto.BusStopListResponse;
import biz.ugur.busroutebackend.transport.application.dto.BusStopResponse;
import biz.ugur.busroutebackend.transport.application.usecase.CreateBusStopUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.DeleteBusStopUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.GetAllBusStopsUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.UpdateBusStopUseCase;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/stops")
@Slf4j
@CrossOrigin(origins = "*")
public class AdminStopController {

    private final CreateBusStopUseCase createBusStopUseCase;
    private final GetAllBusStopsUseCase getAllBusStopsUseCase;
    private final UpdateBusStopUseCase updateBusStopUseCase;
    private final DeleteBusStopUseCase deleteBusStopUseCase;

    public AdminStopController(CreateBusStopUseCase createBusStopUseCase,
                               GetAllBusStopsUseCase getAllBusStopsUseCase,
                               UpdateBusStopUseCase updateBusStopUseCase,
                               DeleteBusStopUseCase deleteBusStopUseCase) {
        this.createBusStopUseCase = createBusStopUseCase;
        this.getAllBusStopsUseCase = getAllBusStopsUseCase;
        this.updateBusStopUseCase = updateBusStopUseCase;
        this.deleteBusStopUseCase = deleteBusStopUseCase;
    }

    @PostMapping
    public Mono<ResponseEntity<BusStopResponse>> createStop(@Valid @RequestBody BusStopCreateRequest request) {
        log.info("Creating bus stop: {}", request.getStopName());

        return createBusStopUseCase.execute(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Bus stop created successfully: {}", request.getStopName());
                    }
                })
                .doOnError(error -> log.error("Failed to create bus stop: {}", request.getStopName(), error));
    }

    @GetMapping
    public Mono<ResponseEntity<BusStopListResponse>> getAllStops(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Boolean active) {

        log.debug("Fetching bus stops (page: {}, size: {}, active: {})", page, size, active);

        return getAllBusStopsUseCase.execute(new GetAllBusStopsUseCase.Request(page, size, active))
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> {
                    if (response.getBody() != null) {
                        log.debug("Retrieved {} bus stops", response.getBody().getTotalCount());
                    }
                });
    }

    @PutMapping("/{stopId}")
    public Mono<ResponseEntity<BusStopResponse>> updateStop(
            @PathVariable String stopId,
            @Valid @RequestBody BusStopCreateRequest request) {

        log.info("Updating bus stop: {}", stopId);

        return updateBusStopUseCase.execute(new UpdateBusStopUseCase.Request(stopId, request))
                .map(ResponseEntity::ok)
                .onErrorReturn(IllegalArgumentException.class,
                        ResponseEntity.notFound().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Bus stop updated successfully: {}", stopId);
                    }
                })
                .doOnError(error -> log.error("Failed to update bus stop: {}", stopId, error));
    }

    @DeleteMapping("/{stopId}")
    public Mono<ResponseEntity<Void>> deleteStop(@PathVariable String stopId) {
        log.info("Deleting bus stop: {}", stopId);

        return deleteBusStopUseCase.execute(stopId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorReturn(IllegalArgumentException.class,
                        ResponseEntity.notFound().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Bus stop deleted successfully: {}", stopId);
                    }
                })
                .doOnError(error -> log.error("Failed to delete bus stop: {}", stopId, error));
    }
}
