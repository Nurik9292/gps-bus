package biz.ugur.busroutebackend.routing.domain.repository;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface BusRouteConnectionRepository {


    Flux<BusRoute> findConnectingRoutes(BusStop fromStop, BusStop toStop);

    Mono<Boolean> areStopsConnected(BusStop stop1, BusStop stop2);
}
