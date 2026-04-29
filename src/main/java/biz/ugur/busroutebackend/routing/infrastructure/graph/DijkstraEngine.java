package biz.ugur.busroutebackend.routing.infrastructure.graph;

import biz.ugur.busroutebackend.routing.domain.model.graph.EdgeType;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitEdge;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitGraph;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitPath;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitPathSegment;
import biz.ugur.busroutebackend.routing.infrastructure.config.DijkstraProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

@Component
@Slf4j
public class DijkstraEngine {

    private final DijkstraProperties properties;

    public DijkstraEngine(DijkstraProperties properties) {
        this.properties = properties;
    }

    public List<TransitPath> findPaths(TransitGraph graph, String fromStopId, String toStopId) {
        return findPaths(graph, fromStopId, toStopId, properties.getKPaths());
    }

    public List<TransitPath> findPaths(TransitGraph graph, String fromStopId, String toStopId, int maxPaths) {
        if (fromStopId.equals(toStopId)) return Collections.emptyList();
        if (graph.getStop(fromStopId) == null || graph.getStop(toStopId) == null) {
            return Collections.emptyList();
        }

        List<TransitPath> results = new ArrayList<>();
        Set<String> penalizedRoutes = new HashSet<>();
        boolean singleSharedRouteScenario = onlySharedSingleBusRoute(graph, fromStopId, toStopId);

        for (int k = 0; k < maxPaths; k++) {
            TransitPath path = runDijkstra(graph, fromStopId, toStopId, penalizedRoutes);
            if (path == null) break;

            results.add(path);
            penalizedRoutes.addAll(path.usedRouteIds());

            if (singleSharedRouteScenario) break;
        }

        return results;
    }

    private boolean onlySharedSingleBusRoute(TransitGraph graph, String fromStopId, String toStopId) {
        Set<String> fromRoutes = graph.getBusRouteIdsAtStop(fromStopId);
        Set<String> toRoutes = graph.getBusRouteIdsAtStop(toStopId);
        return fromRoutes.size() == 1
                && toRoutes.size() == 1
                && fromRoutes.equals(toRoutes);
    }

    private TransitPath runDijkstra(TransitGraph graph, String fromStopId, String toStopId,
                                    Set<String> penalizedRoutes) {
        PriorityQueue<DijkstraState> pq = new PriorityQueue<>(Comparator.comparingInt(s -> s.cost));
        Map<String, Integer> dist = new HashMap<>();
        Map<String, EdgeRecord> prev = new HashMap<>();

        String startKey = stateKey(fromStopId, null);
        dist.put(startKey, 0);
        pq.offer(new DijkstraState(0, fromStopId, null, 0));

        int iterations = 0;
        int maxIterations = properties.getMaxIterations();
        int maxCostMinutes = properties.getMaxCostMinutes();
        while (!pq.isEmpty()) {
            if (++iterations > maxIterations) {
                log.warn("Dijkstra exceeded {} iterations from {} to {} — aborting",
                        maxIterations, fromStopId, toStopId);
                return null;
            }

            DijkstraState cur = pq.poll();
            if (cur.cost > maxCostMinutes) continue;

            String curKey = stateKey(cur.stopId, cur.lastBusRouteId);

            Integer bestCost = dist.get(curKey);
            if (bestCost != null && bestCost < cur.cost) continue;

            if (cur.stopId.equals(toStopId)) {
                return reconstructPath(cur.cost, cur.transfers, curKey, startKey, prev);
            }

            for (TransitEdge edge : graph.getEdges(cur.stopId)) {
                int edgeCost = edge.weightMinutes();
                int newTransfers = cur.transfers;
                String newLastBusRouteId = cur.lastBusRouteId;
                int penalty = 0;

                if (edge.type() == EdgeType.BUS_RIDE) {
                    String edgeRouteId = edge.routeId();

                    if (cur.lastBusRouteId != null && !cur.lastBusRouteId.equals(edgeRouteId)) {
                        newTransfers = cur.transfers + 1;
                        if (newTransfers > properties.getMaxTransfers()) continue;
                        penalty = properties.getTransferPenaltyMinutes();
                    }
                    newLastBusRouteId = edgeRouteId;

                    if (penalizedRoutes.contains(edgeRouteId)) {
                        edgeCost += properties.getRoutePenaltyMinutes();
                    }
                }

                int newCost = cur.cost + edgeCost + penalty;
                String nextKey = stateKey(edge.toStopId(), newLastBusRouteId);

                if (!dist.containsKey(nextKey) || dist.get(nextKey) > newCost) {
                    dist.put(nextKey, newCost);
                    prev.put(nextKey, new EdgeRecord(curKey, cur.stopId, edge));
                    pq.offer(new DijkstraState(newCost, edge.toStopId(), newLastBusRouteId, newTransfers));
                }
            }
        }

        return null;
    }

    private TransitPath reconstructPath(int totalCost, int transfers,
                                        String finalKey, String startKey,
                                        Map<String, EdgeRecord> prev) {
        List<TransitPathSegment> segments = new ArrayList<>();
        String curKey = finalKey;

        while (!curKey.equals(startKey) && prev.containsKey(curKey)) {
            EdgeRecord rec = prev.get(curKey);
            segments.add(0, new TransitPathSegment(
                    rec.fromStopId(), rec.edge().toStopId(),
                    rec.edge().type(),
                    rec.edge().weightMinutes(),
                    rec.edge().routeNumber(), rec.edge().routeId(),
                    rec.edge().direction()
            ));
            curKey = rec.prevKey();
        }

        return new TransitPath(segments, totalCost, transfers);
    }

    private String stateKey(String stopId, String lastBusRouteId) {
        return stopId + "#" + (lastBusRouteId != null ? lastBusRouteId : "");
    }

    private record DijkstraState(
            int cost,
            String stopId,
            String lastBusRouteId, 
            int transfers
    ) {}

    private record EdgeRecord(String prevKey, String fromStopId, TransitEdge edge) {}
}
