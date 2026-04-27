package biz.ugur.busroutebackend.routing.domain.repository;

import reactor.core.publisher.Flux;

public interface TransitGraphDataRepository {

 
    Flux<BusEdgeRecord> findAllConsecutiveStopEdges();

    Flux<WalkingEdgeRecord> findAllWalkingEdges(double maxWalkingMeters);

    record BusEdgeRecord(
            String fromStopId,
            String toStopId,
            String routeId,
            String routeNumber,
            int direction,
            int weightMinutes
    ) {}

    record WalkingEdgeRecord(
            String stopId1,
            String stopId2,
            int walkingMinutes
    ) {}
}
