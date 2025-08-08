package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.request.stop.BusStopCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.stop.BusStopUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.stop.BusStopListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.stop.BusStopResponse;
import biz.ugur.busroutebackend.transport.application.dto.stop.GetAllStopPaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopResult;
import biz.ugur.busroutebackend.transport.application.usecase.stop.CreateBusStopUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.stop.DeleteBusStopUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.stop.GetAllBusStopsUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.stop.UpdateBusStopUseCase;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
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

    @GetMapping
    public Mono<ResponseEntity<BusStopListResponse>> getAllStops(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Boolean active) {

        log.debug("Fetching bus stops (page: {}, size: {}, active: {})", page, size, active);

        return Mono.just(GetAllStopPaginationQuery.fromParams(page, size, sort, order, active))
                .as(getAllBusStopsUseCase::execute)
                .map(BusStopListResponse::fromResult)
                .doOnNext(response -> log.debug("Retrieved {} stops", response.getTotalCount()))
                .map(ResponseEntity::ok);

    }

    @PostMapping
    public Mono<ResponseEntity<BusStopResponse>> createStop(@Valid @RequestBody BusStopCreateRequest request) {
        log.info("Creating bus stop: {}", request.getStopName());
        System.out.println("log log " + request);
        return Mono.just(request.toInput())
                .as(createBusStopUseCase::execute)
                .map(this::toStopResponseEntity)
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Bus stop created successfully: {}", request.getStopName());
                    }
                })
                .doOnError(error -> log.error("Failed to create bus stop: {}", request.getStopName(), error));
    }



    @PutMapping("/{stopId}")
    public Mono<ResponseEntity<BusStopResponse>> updateStop(@PathVariable String stopId,
            @Valid @RequestBody BusStopUpdateRequest request) {
        log.info("Updating bus stop: {}", stopId);

        System.out.println("test test test updateStop: " + request);

        return Mono.just(request.toInput(stopId))
                .as(updateBusStopUseCase::execute)
                .map(this::toStopResponseEntity)
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

        return Mono.just(stopId)
                .as(deleteBusStopUseCase::execute)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))

                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Bus stop deleted successfully: {}", stopId);
                    }
                })
                .doOnError(error -> log.error("Failed to delete bus stop: {}", stopId, error));


    }

    private ResponseEntity<BusStopResponse> toStopResponseEntity(StopResult result) {
        return ResponseEntity.ok().body(BusStopResponse.fromResult(result));
    }

}
