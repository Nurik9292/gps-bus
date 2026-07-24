package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentBaseline;
import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SegmentTravelStatsRepository {

    Mono<SegmentTravelStat> findByKey(String routeId, int direction,
                                      String fromStopId, String toStopId,
                                      int hourOfDay, boolean weekend);

    Mono<SegmentTravelStat> save(SegmentTravelStat stat);

    Flux<SegmentTravelStat> findAll();

    Mono<SegmentBaseline> findEdgeBaseline(String fromStopId, String toStopId,
                                           int hourOfDay, boolean weekend);

    Flux<SegmentTravelStat> findByHourAndWeekend(int hourOfDay, boolean weekend);
}
