package biz.ugur.busroutebackend.interfaces.rest.transport.V2.controller;

import biz.ugur.busroutebackend.interfaces.rest.transport.V2.response.RouteDetailV2;
import biz.ugur.busroutebackend.interfaces.rest.transport.V2.response.RouteSummaryV2;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetAllBusRoutesUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetRouteByIdUseCase;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v2/routes")
public class RouteV2Controller extends BaseController {

    private final GetRouteByIdUseCase getRouteByIdUseCase;
    private final GetAllBusRoutesUseCase getAllBusRoutesUseCase;

    public RouteV2Controller(MessageSource messageSource,
                             GetRouteByIdUseCase getRouteByIdUseCase,
                             GetAllBusRoutesUseCase getAllBusRoutesUseCase) {
        super(messageSource);
        this.getRouteByIdUseCase = getRouteByIdUseCase;
        this.getAllBusRoutesUseCase = getAllBusRoutesUseCase;
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<RouteSummaryV2>>>> getAllRoutes() {
        return ok(getAllBusRoutesUseCase.execute(Mono.empty())
                .map(routeList -> routeList.items().stream()
                        .map(RouteSummaryV2::fromRouteData)
                        .toList()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<RouteDetailV2>>> getRouteById(@PathVariable String id) {
        return okOrNotFound(
                Mono.just(new GetRouteByIdUseCase.Query(id))
                        .as(getRouteByIdUseCase::execute)
                        .map(RouteDetailV2::fromRouteData));
    }

    @Override
    protected String getControllerName() {
        return RouteV2Controller.class.getSimpleName();
    }
}
