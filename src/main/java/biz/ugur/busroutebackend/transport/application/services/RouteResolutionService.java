package biz.ugur.busroutebackend.transport.application.services;

import biz.ugur.busroutebackend.transport.application.dto.route.ResolvedRouteData;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface RouteResolutionService {

    Mono<ResolvedRouteData> resolveById(String routeId);

    Mono<ResolvedRouteData> resolveByNumber(String routeNumber);

    Flux<ResolvedRouteData> resolveAllRoutes();
}
