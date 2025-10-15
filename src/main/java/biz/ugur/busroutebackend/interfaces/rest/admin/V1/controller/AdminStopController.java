package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.stop.BusStopCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.stop.BusStopUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.stop.BusStopListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.stop.BusStopResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.dto.stop.GetAllStopPaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopData;
import biz.ugur.busroutebackend.transport.application.usecase.stop.CreateBusStopUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.stop.DeleteBusStopUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.stop.GetAllBusStopsUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.stop.UpdateBusStopUseCase;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN;

@RestController
@RequestMapping(V1_ADMIN + "/stops")
@CrossOrigin(origins = "*")
public class AdminStopController extends BaseController {

    private final CreateBusStopUseCase createBusStopUseCase;
    private final GetAllBusStopsUseCase getAllBusStopsUseCase;
    private final UpdateBusStopUseCase updateBusStopUseCase;
    private final DeleteBusStopUseCase deleteBusStopUseCase;

    public AdminStopController(CreateBusStopUseCase createBusStopUseCase,
                               GetAllBusStopsUseCase getAllBusStopsUseCase,
                               UpdateBusStopUseCase updateBusStopUseCase,
                               DeleteBusStopUseCase deleteBusStopUseCase,
                               MessageSource messageSource) {
        super(messageSource);

        this.createBusStopUseCase = createBusStopUseCase;
        this.getAllBusStopsUseCase = getAllBusStopsUseCase;
        this.updateBusStopUseCase = updateBusStopUseCase;
        this.deleteBusStopUseCase = deleteBusStopUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminStopController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<BusStopListResponse>>> getAllStops(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Boolean active) {

        return ok(Mono.just(GetAllStopPaginationQuery.fromParams(page, size, camelToSnake(sort), order, active))
                .as(getAllBusStopsUseCase::execute)
                .map(BusStopListResponse::fromResult));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<BusStopResponse>>> createStop(@Valid @RequestBody BusStopCreateRequest request) {

        return created(Mono.just(request.toInput())
                .as(createBusStopUseCase::execute)
                .map(this::toBasic));
    }



    @PutMapping("/{stopId}")
    public Mono<ResponseEntity<ApiResponse<BusStopResponse>>> updateStop(@PathVariable String stopId,
            @Valid @RequestBody BusStopUpdateRequest request) {

        return ok(Mono.just(request.toInput(stopId))
                .as(updateBusStopUseCase::execute)
                .map(this::toBasic));
    }

    @DeleteMapping("/{stopId}")
    public Mono<ResponseEntity<Void>> deleteStop(@PathVariable String stopId) {

        return Mono.just(stopId)
                .as(deleteBusStopUseCase::execute)
                .then(noContent());
    }

    private BusStopResponse toBasic (StopData stopData) {
        return BusStopResponse.fromResult(stopData);
    }

}
