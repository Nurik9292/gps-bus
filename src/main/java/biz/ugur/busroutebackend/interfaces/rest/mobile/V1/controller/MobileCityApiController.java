package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.client.application.usecase.city.GetMobileCitiesUseCase;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.MobileCityListResponse;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.MobileCityResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_MOBILE_CITIES;

@RestController
@RequestMapping(V1_MOBILE_CITIES)
public class MobileCityApiController extends BaseController {

    private final GetMobileCitiesUseCase getMobileCitiesUseCase;

    public MobileCityApiController(GetMobileCitiesUseCase getMobileCitiesUseCase,
                                   MessageSource messageSource) {
        super(messageSource);
        this.getMobileCitiesUseCase = getMobileCitiesUseCase;
    }

    @Override
    protected String getControllerName() {
        return MobileCityApiController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<MobileCityListResponse>>> getAllCities() {
        return ok(getMobileCitiesUseCase.execute(null)
                .map(cities -> {
                    var cityResponses = cities.stream()
                            .map(MobileCityResponse::from)
                            .toList();
                    return MobileCityListResponse.builder()
                            .cities(cityResponses)
                            .count(cityResponses.size())
                            .build();
                }));
    }
}
