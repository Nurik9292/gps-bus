package biz.ugur.busroutebackend.interfaces.rest.routing.V2.controller;

import biz.ugur.busroutebackend.interfaces.rest.routing.V1.request.TripSearchRequest;
import biz.ugur.busroutebackend.interfaces.rest.routing.V2.response.TripSearchResponseV2;
import biz.ugur.busroutebackend.routing.application.usecase.SearchTripsV2UseCase;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V2_ROUTING;

@RestController
@RequestMapping(V2_ROUTING)
@Slf4j
public class TripPlanningV2Controller extends BaseController {

    private final SearchTripsV2UseCase searchTripsUseCase;

    public TripPlanningV2Controller(SearchTripsV2UseCase searchTripsUseCase, MessageSource messageSource) {
        super(messageSource);
        this.searchTripsUseCase = searchTripsUseCase;
    }

    @Override
    protected String getControllerName() {
        return TripPlanningV2Controller.class.getSimpleName();
    }

    @PostMapping("/search")
    @RateLimiter(name = "searchApi")
    public Mono<ResponseEntity<ApiResponse<TripSearchResponseV2>>> searchTrips(
            @Valid @RequestBody TripSearchRequest request) {
        return ok(Mono.just(request).as(searchTripsUseCase::execute));
    }
}
