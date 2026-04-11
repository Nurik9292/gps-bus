package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.valueobject.StopDwellStat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StopDwellStatsRepository {

    Mono<StopDwellStat> findByStopRouteDirection(String stopId, String routeNumber, int direction);
  
    Flux<StopDwellStat> findByRouteAndDirection(String routeNumber, int direction);
  
    Mono<StopDwellStat> save(StopDwellStat stat);

    Flux<StopDwellStat> findAll();
}
