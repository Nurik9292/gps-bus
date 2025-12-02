package biz.ugur.busroutebackend.interfaces.rest.duralga;

import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.MobileRouteResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetRouteByNumberUseCase;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/duralga")
public class DuralgaTmpController extends BaseController {

    private final GetRouteByNumberUseCase getRouteByNumberUseCase;

    public DuralgaTmpController(MessageSource messageSource,
                                GetRouteByNumberUseCase getRouteByNumberUseCase) {
        super(messageSource);
        this.getRouteByNumberUseCase = getRouteByNumberUseCase;
    }

    @GetMapping("/{routeNumber}")
    public Mono<ResponseEntity<BaseController.ApiResponse<MobileRouteResponse>>> getRouteByNumber(
            @PathVariable String routeNumber
    ) {
        return Mono.just(new GetRouteByNumberUseCase.Query(routeNumber))
                .as(getRouteByNumberUseCase::execute)
                .map(result -> MobileRouteResponse.from(result, true))
                .map(ApiResponse::success)
                .map(ResponseEntity::ok);
    }

    @Override
    protected String getControllerName() {
        return DuralgaTmpController.class.getSimpleName();
    }
}
