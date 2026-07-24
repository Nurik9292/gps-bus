package biz.ugur.busroutebackend.interfaces.rest.duralga;

import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.MobileRouteAlternativesResponse;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.MobileRouteResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetRouteByIdUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetRouteByNumberUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.routealternative.GetActiveAlternativesForRouteUseCase;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/duralga")
public class DuralgaTmpController extends BaseController {

    private final GetRouteByNumberUseCase getRouteByNumberUseCase;
    private final GetRouteByIdUseCase getRouteByIdUseCase;
    private final GetActiveAlternativesForRouteUseCase getActiveAlternativesForRouteUseCase;

    public DuralgaTmpController(MessageSource messageSource,
                                GetRouteByNumberUseCase getRouteByNumberUseCase,
                                GetRouteByIdUseCase getRouteByIdUseCase,
                                GetActiveAlternativesForRouteUseCase getActiveAlternativesForRouteUseCase) {
        super(messageSource);
        this.getRouteByNumberUseCase = getRouteByNumberUseCase;
        this.getRouteByIdUseCase = getRouteByIdUseCase;
        this.getActiveAlternativesForRouteUseCase = getActiveAlternativesForRouteUseCase;
    }

    @GetMapping("/{routeNumber}")
    public Mono<ResponseEntity<ApiResponse<MobileRouteResponse>>> getRouteByNumber(
            @PathVariable String routeNumber,
            @RequestParam(required = false) String cityId
    ) {
        return Mono.just(new GetRouteByNumberUseCase.Query(routeNumber, cityId))
                .as(getRouteByNumberUseCase::execute)
                .map(result -> MobileRouteResponse.from(result, true))
                .map(ApiResponse::success)
                .map(ResponseEntity::ok);
    }

    @Override
    protected String getControllerName() {
        return DuralgaTmpController.class.getSimpleName();
    }

    @GetMapping("/{routeId}/alternatives")
    public Mono<ResponseEntity<ApiResponse<MobileRouteAlternativesResponse>>> getRouteAlternatives(@PathVariable String routeId) {
        return Mono.just(new GetRouteByIdUseCase.Query(routeId))
                .as(getRouteByIdUseCase::execute)
                .flatMap(primaryRoute ->
                        getActiveAlternativesForRouteUseCase.execute(
                                        new GetActiveAlternativesForRouteUseCase.Request(routeId)
                                )
                                .map(routeData -> MobileRouteResponse.from(routeData, false))
                                .collectList()
                                .map(alternatives ->
                                        MobileRouteAlternativesResponse.builder()
                                                .primaryRouteId(routeId)
                                                .primaryRouteNumber(primaryRoute.routeNumber())
                                                .primaryRouteIsActive(primaryRoute.isActive())
                                                .alternatives(alternatives)
                                                .alternativesCount(alternatives.size())
                                                .build()
                                )
                )
                .map(ApiResponse::success)
                .map(ResponseEntity::ok);
    }
}
