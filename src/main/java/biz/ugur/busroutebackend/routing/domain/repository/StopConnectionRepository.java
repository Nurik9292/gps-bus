package biz.ugur.busroutebackend.routing.domain.repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StopConnectionRepository {

    Flux<StopConnection> findAllConnections();

    Flux<StopConnection> findConnectionsFromStop(String stopId);

    Mono<Boolean> areStopsConnected(String fromStopId, String toStopId);

    Mono<ShortestPath> findShortestPath(String fromStopId, String toStopId, int maxTransfers);

    record StopConnection(
            String fromStopId,
            String toStopId,
            String routeId,
            String routeNumber,
            int estimatedTravelMinutes,
            int stopSequenceDifference
    ) {}

    record ShortestPath(
            String fromStopId,
            String toStopId,
            int totalTravelMinutes,
            int transfersCount,
            java.util.List<PathSegment> segments
    ) {}

    record PathSegment(
            String fromStopId,
            String toStopId,
            String routeNumber,
            int travelMinutes
    ) {}
}