package biz.ugur.busroutebackend.routing.domain.repository;

import reactor.core.publisher.Flux;

/**
 * Repository for raw data needed to build the in-memory transit graph.
 */
public interface TransitGraphDataRepository {

    /**
     * Returns all consecutive stop pairs for active routes.
     * Each record represents a directed bus-ride edge from one stop to the next.
     */
    Flux<BusEdgeRecord> findAllConsecutiveStopEdges();

    /**
     * Returns pairs of stops that are within walking distance of each other.
     * @param maxWalkingMeters maximum distance in meters
     */
    Flux<WalkingEdgeRecord> findAllWalkingEdges(double maxWalkingMeters);

    record BusEdgeRecord(
            String fromStopId,
            String toStopId,
            String routeId,
            String routeNumber,
            int weightMinutes
    ) {}

    record WalkingEdgeRecord(
            String stopId1,
            String stopId2,
            int walkingMinutes
    ) {}
}
