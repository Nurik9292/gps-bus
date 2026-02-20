package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.services.WalkingRouteService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnMissingBean(WalkingRouteService.class)
public class NoOpWalkingRouteService implements WalkingRouteService {

    @Override
    public Mono<WalkingRouteResult> getWalkingRoute(Coordinates from, Coordinates to) {
        return Mono.just(WalkingRouteResult.EMPTY);
    }
}
