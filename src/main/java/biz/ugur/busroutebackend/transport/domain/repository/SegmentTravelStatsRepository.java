package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SegmentTravelStatsRepository {

    Mono<SegmentTravelStat> findByKey(String routeNumber, int direction,
                                      String fromStopId, String toStopId,
                                      int hourOfDay, boolean weekend);

    Mono<SegmentTravelStat> save(SegmentTravelStat stat);

    Flux<SegmentTravelStat> findAll();
}
