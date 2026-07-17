package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.route.BusRouteCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.route.BusRouteUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.route.BusRouteListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.route.BusRouteResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.route.CheckRouteNumberResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.route.RouteSelectOption;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import biz.ugur.busroutebackend.transport.application.dto.route.GetAllRoutePaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.usecase.route.*;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_ROUTES;

@RestController
@RequestMapping(V1_ADMIN_ROUTES)
public class AdminRouteController extends BasePaginatedController {

    private final CreateBusRouteUseCase createBusRouteUseCase;
    private final GetAllBusRoutesWithPaginationUseCase getAllBusRoutesUseCase;
    private final UpdateBusRouteUseCase updateBusRouteUseCase;
    private final DeleteBusRouteUseCase deleteBusRouteUseCase;
    private final CheckRouteNumberUseCase checkRouteNumberUseCase;
    private final GetAllBusRoutesUseCase getAllRoutesUseCase;
    private final GetRouteByIdUseCase getRouteByIdUseCase;
    private final BusRouteRepository busRouteRepository;
    private final RouteGeometryCache routeGeometryCache;
    private final biz.ugur.busroutebackend.transport.application.usecase.route.RestoreRouteNameUseCase restoreRouteNameUseCase;
    private final biz.ugur.busroutebackend.transport.domain.repository.NameHistoryRepository nameHistoryRepository;


    public AdminRouteController(CreateBusRouteUseCase createBusRouteUseCase,
                                GetAllBusRoutesWithPaginationUseCase getAllBusRoutesUseCase,
                                UpdateBusRouteUseCase updateBusRouteUseCase,
                                DeleteBusRouteUseCase deleteBusRouteUseCase,
                                CheckRouteNumberUseCase checkRouteNumberUseCase,
                                GetAllBusRoutesUseCase getAllRoutesUseCase,
                                GetRouteByIdUseCase getRouteByIdUseCase,
                                BusRouteRepository busRouteRepository,
                                RouteGeometryCache routeGeometryCache,
                                biz.ugur.busroutebackend.transport.application.usecase.route.RestoreRouteNameUseCase restoreRouteNameUseCase,
                                biz.ugur.busroutebackend.transport.domain.repository.NameHistoryRepository nameHistoryRepository,
                                MessageSource messageSource) {
        super(messageSource);
        this.createBusRouteUseCase = createBusRouteUseCase;
        this.getAllBusRoutesUseCase = getAllBusRoutesUseCase;
        this.updateBusRouteUseCase = updateBusRouteUseCase;
        this.deleteBusRouteUseCase = deleteBusRouteUseCase;
        this.checkRouteNumberUseCase = checkRouteNumberUseCase;
        this.getAllRoutesUseCase = getAllRoutesUseCase;
        this.getRouteByIdUseCase = getRouteByIdUseCase;
        this.busRouteRepository = busRouteRepository;
        this.routeGeometryCache = routeGeometryCache;
        this.restoreRouteNameUseCase = restoreRouteNameUseCase;
        this.nameHistoryRepository = nameHistoryRepository;
    }

    @Override
    protected String getControllerName() {
        return AdminRouteController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<BusRouteListResponse>>> getAllRoutes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String query,
            @RequestParam(required = false, name = "cityId") String cityId) {

        validatePagination(page, size);

        return ok(Mono.just(GetAllRoutePaginationQuery.fromParams(page, size, camelToSnake(sort), order, active, query, cityId))
                .as(getAllBusRoutesUseCase::execute)
                .map(BusRouteListResponse::fromResult));
    }

    @GetMapping("/all")
    public Mono<ResponseEntity<ApiResponse<BusRouteListResponse>>> getAll() {

      return ok(getAllRoutesUseCase.execute(Mono.empty())
              .map(BusRouteListResponse::fromResult));
    }

 
    @GetMapping("/select-options")
    public Mono<ResponseEntity<ApiResponse<List<RouteSelectOption>>>> getSelectOptions() {
        return ok(busRouteRepository.findActiveRouteSelectInfo()
                .map(info -> new RouteSelectOption(
                        info.id(),
                        info.routeNumber(),
                        info.routeName(),
                        info.cityId(),
                        info.cityName()
                ))
                .collectList());
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<BusRouteResponse>>> getRouteById(@PathVariable String id) {

        return ok(Mono.just(new GetRouteByIdUseCase.Query(id))
                .as(getRouteByIdUseCase::execute)
                .map(this::toBasic));
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

    @PostMapping("/{routeNumber}/refresh-cache")
    public Mono<ResponseEntity<Void>> refreshRouteCache(@PathVariable String routeNumber) {
        return routeGeometryCache.refreshRoute(routeNumber)
                .then(noContent());
    }

    public record RestoreNameRequest(String field) {
    }

    @GetMapping("/{routeId}/name-history")
    public Mono<ResponseEntity<ApiResponse<java.util.List<biz.ugur.busroutebackend.transport.application.dto.NameChangeResult>>>> getNameHistory(
            @PathVariable String routeId) {
        return ok(nameHistoryRepository
                .findByEntity(biz.ugur.busroutebackend.transport.application.services.NameHistoryRecorder.KIND_ROUTE, routeId)
                .map(biz.ugur.busroutebackend.transport.application.dto.NameChangeResult::fromDomain)
                .collectList());
    }

    @PostMapping("/{routeId}/name-history/restore")
    public Mono<ResponseEntity<ApiResponse<BusRouteResponse>>> restoreName(@PathVariable String routeId,
            @RequestBody RestoreNameRequest request) {
        return ok(Mono.just(new biz.ugur.busroutebackend.transport.application.usecase.route.RestoreRouteNameUseCase.Query(routeId, request.field()))
                .as(restoreRouteNameUseCase::execute)
                .map(this::toBasic));
    }

    private BusRouteResponse toBasic(RouteData  routeData) {
        return BusRouteResponse.fromResult(routeData);
    }


}
