package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.route.BusRouteCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.route.BusRouteUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.route.BusRouteListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.route.BusRouteResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.route.CheckRouteNumberResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.dto.route.GetAllRoutePaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.usecase.route.*;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN;

@RestController
@RequestMapping(V1_ADMIN + "/routes")
@CrossOrigin(origins = "*")
public class AdminRouteController extends BaseController {

    private final CreateBusRouteUseCase createBusRouteUseCase;
    private final GetAllBusRoutesWithPaginationUseCase getAllBusRoutesUseCase;
    private final UpdateBusRouteUseCase updateBusRouteUseCase;
    private final DeleteBusRouteUseCase deleteBusRouteUseCase;
    private final CheckRouteNumberUseCase checkRouteNumberUseCase;
    private final GetAllBusRoutesUseCase getAllRoutesUseCase;


    public AdminRouteController(CreateBusRouteUseCase createBusRouteUseCase,
                                GetAllBusRoutesWithPaginationUseCase getAllBusRoutesUseCase,
                                UpdateBusRouteUseCase updateBusRouteUseCase,
                                DeleteBusRouteUseCase deleteBusRouteUseCase,
                                CheckRouteNumberUseCase checkRouteNumberUseCase,
                                GetAllBusRoutesUseCase getAllRoutesUseCase,
                                MessageSource messageSource) {
        super(messageSource);
        this.createBusRouteUseCase = createBusRouteUseCase;
        this.getAllBusRoutesUseCase = getAllBusRoutesUseCase;
        this.updateBusRouteUseCase = updateBusRouteUseCase;
        this.deleteBusRouteUseCase = deleteBusRouteUseCase;
        this.checkRouteNumberUseCase = checkRouteNumberUseCase;
        this.getAllRoutesUseCase = getAllRoutesUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminRouteController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<BusRouteListResponse>>> getAllRoutes(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Boolean active) {

        return ok(Mono.just(GetAllRoutePaginationQuery.fromParams(page, size, camelToSnake(sort), order, active))
                .as(getAllBusRoutesUseCase::execute)
                .map(BusRouteListResponse::fromResult));
    }

    @GetMapping("/all")
    public Mono<ResponseEntity<ApiResponse<BusRouteListResponse>>> getAll() {

      return ok(getAllRoutesUseCase.execute(Mono.empty())
              .map(BusRouteListResponse::fromResult));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<BusRouteResponse>>> createRoute(@Valid @RequestBody BusRouteCreateRequest request) {

        return created( Mono.just(request.toCommand())
                .as(createBusRouteUseCase::execute)
                .map(this::toBasic));

    }


    @PutMapping("/{routeId}")
    public Mono<ResponseEntity<ApiResponse<BusRouteResponse>>> updateRoute(@PathVariable String routeId,
            @Valid @RequestBody BusRouteUpdateRequest request) {

        return ok(Mono.just(request.toCommand(routeId))
                .as(updateBusRouteUseCase::execute)
                .map(this::toBasic));
    }


    @DeleteMapping("/{routeId}")
    public Mono<ResponseEntity<Void>> deleteRoute(@PathVariable String routeId) {

        return deleteBusRouteUseCase.execute(routeId)
                .then(noContent());
    }

    @GetMapping("/check-availability")
    public Mono<ResponseEntity<ApiResponse<CheckRouteNumberResponse>>> checkRouteAvailability(@RequestParam(required = false) String routeNumber) {

        return ok(Mono.just(routeNumber)
                .as(checkRouteNumberUseCase::execute)
                .map(CheckRouteNumberResponse::of));
    }

    private BusRouteResponse toBasic(RouteData  routeData) {
        return BusRouteResponse.fromResult(routeData);
    }


}
