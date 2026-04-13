package biz.ugur.busroutebackend.routing.infrastructure.graph;

import biz.ugur.busroutebackend.routing.domain.model.graph.EdgeType;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitEdge;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitGraph;
import biz.ugur.busroutebackend.routing.domain.repository.TransitGraphDataRepository;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransitGraphBuilder {

    private final BusStopRepository stopRepository;
    private final BusRouteRepository routeRepository;
    private final TransitGraphDataRepository graphDataRepository;

    private static final double MAX_WALKING_METERS = 800.0;

    public Mono<TransitGraph> build() {
        log.info("🔨 Building transit graph...");

        Mono<Map<String, BusStop>> stopsMono = stopRepository.findActiveStops()
                .collectMap(stop -> stop.getId().getValue());

        Mono<Map<String, BusRoute>> routesMono = routeRepository.findActiveRoutes()
                .collectMap(route -> route.getId().getValue());

        Mono<List<TransitGraphDataRepository.BusEdgeRecord>> busEdgesMono =
                graphDataRepository.findAllConsecutiveStopEdges().collectList();

        Mono<List<TransitGraphDataRepository.WalkingEdgeRecord>> walkingEdgesMono =
                graphDataRepository.findAllWalkingEdges(MAX_WALKING_METERS).collectList();

        return Mono.zip(stopsMono, routesMono, busEdgesMono, walkingEdgesMono)
                .map(tuple -> {
                    Map<String, BusStop> stops = tuple.getT1();
                    Map<String, BusRoute> routes = tuple.getT2();
                    List<TransitGraphDataRepository.BusEdgeRecord> busEdges = tuple.getT3();
                    List<TransitGraphDataRepository.WalkingEdgeRecord> walkingEdges = tuple.getT4();

                    Map<String, List<TransitEdge>> adj = new HashMap<>(stops.size() * 2);

                    for (String stopId : stops.keySet()) {
                        adj.put(stopId, new ArrayList<>());
                    }

                    int busEdgeCount = 0;
                    for (TransitGraphDataRepository.BusEdgeRecord edge : busEdges) {
                        List<TransitEdge> list = adj.computeIfAbsent(edge.fromStopId(), k -> new ArrayList<>());
                        list.add(new TransitEdge(
                                edge.toStopId(),
                                EdgeType.BUS_RIDE,
                                edge.weightMinutes(),
                                edge.routeNumber(),
                                edge.routeId()
                        ));
                        busEdgeCount++;
                    }

                    int walkEdgeCount = 0;
                    for (TransitGraphDataRepository.WalkingEdgeRecord edge : walkingEdges) {
                        adj.computeIfAbsent(edge.stopId1(), k -> new ArrayList<>())
                                .add(new TransitEdge(edge.stopId2(), EdgeType.WALKING, edge.walkingMinutes(), null, null));
                        adj.computeIfAbsent(edge.stopId2(), k -> new ArrayList<>())
                                .add(new TransitEdge(edge.stopId1(), EdgeType.WALKING, edge.walkingMinutes(), null, null));
                        walkEdgeCount += 2;
                    }

                    TransitGraph graph = new TransitGraph(stops, routes, adj);
                    log.info("✅ Transit graph built: {} stops, {} routes, {} bus edges, {} walking edges",
                            stops.size(), routes.size(), busEdgeCount, walkEdgeCount);
                    return graph;
                });
    }
}
