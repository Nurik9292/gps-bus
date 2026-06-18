package biz.ugur.busroutebackend.interfaces.rest.transport.V2.controller;

import biz.ugur.busroutebackend.interfaces.rest.transport.V2.response.RouteDetailV2;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetRouteByIdUseCase;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v2/routes")
public class RouteV2Controller extends BaseController {

    private final GetRouteByIdUseCase getRouteByIdUseCase;

    public RouteV2Controller(MessageSource messageSource, GetRouteByIdUseCase getRouteByIdUseCase) {
        super(messageSource);
        this.getRouteByIdUseCase = getRouteByIdUseCase;
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
