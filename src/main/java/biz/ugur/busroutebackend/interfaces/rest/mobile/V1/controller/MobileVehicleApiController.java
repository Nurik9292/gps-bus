package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.client.application.usecase.RouteIsFavoriteUseCase;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.MobileVehicleInfoResponse;
import biz.ugur.busroutebackend.transport.application.usecase.ActiveCountVehicleUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.CountVehicleUseCase;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_MOBILE_VEHICLES;

@RestController
@RequestMapping(V1_MOBILE_VEHICLES)
public class MobileVehicleApiController extends BaseMobileController {

    private final CountVehicleUseCase countVehicleUseCase;
    private final ActiveCountVehicleUseCase activeCountVehicleUseCase;

    protected MobileVehicleApiController(MessageSource messageSource,
                                         RequestedContentTypeResolver requestedContentTypeResolver,
                                         RouteIsFavoriteUseCase routeIsFavoriteUseCase,
                                         CountVehicleUseCase countVehicleUseCase,
                                         ActiveCountVehicleUseCase activeCountVehicleUseCase) {
        super(messageSource, requestedContentTypeResolver, routeIsFavoriteUseCase);
        this.countVehicleUseCase = countVehicleUseCase;
        this.activeCountVehicleUseCase = activeCountVehicleUseCase;
    }

    @Override
    protected String getControllerName() {
        return MobileVehicleApiController.class.getSimpleName();
    }

    @GetMapping("/info")
    public Mono<ResponseEntity<ApiResponse<MobileVehicleInfoResponse>>> busesInfo() {

        return ok(Mono.zip(countVehicleUseCase.execute(Mono.empty()), activeCountVehicleUseCase.execute(Mono.empty()))
                .map(tuple ->
                        MobileVehicleInfoResponse.builder()
                                .vehicleCount(tuple.getT1().count())
                                .activeVehicleCount(tuple.getT2().count())
                                .build()

                ));
    }
}
