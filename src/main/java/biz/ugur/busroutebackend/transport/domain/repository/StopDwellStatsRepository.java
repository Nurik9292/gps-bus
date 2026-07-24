package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.valueobject.StopDwellStat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StopDwellStatsRepository {

    Mono<StopDwellStat> findByStopRouteDirection(String stopId, String routeId, int direction);
  
    Flux<StopDwellStat> findByRouteAndDirection(String routeId, int direction);
  
    Mono<StopDwellStat> save(StopDwellStat stat);

    Flux<StopDwellStat> findAll();
}
