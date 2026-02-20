package biz.ugur.busroutebackend.routing.infrastructure.graph;

import biz.ugur.busroutebackend.routing.domain.model.graph.EdgeType;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitEdge;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitGraph;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitPath;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitPathSegment;
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

/**
 * Dijkstra-based path finder for the transit graph.
 *
 * State: (stopId, lastBusRouteId) — the last bus route taken to arrive at stopId.
 * Null lastBusRouteId means: started here, or last move was walking.
 *
 * Transfer counting: a transfer is recorded when boarding a bus whose route differs
 * from the last bus route ridden (lastBusRouteId != edge.routeId).
 *
 * K-paths: run Dijkstra up to K times; after each iteration, penalize the routes
 * used in the found path so the next iteration finds a genuinely different alternative.
 */
@Component
@Slf4j
public class DijkstraEngine {

    private static final int TRANSFER_PENALTY_MINUTES = 5;
    private static final int MAX_TRANSFERS = 2;
    private static final int K_PATHS = 3;
    private static final int ROUTE_PENALTY_MINUTES = 30; // penalty for routes used in previous paths

    /**
     * Find up to K best paths from fromStopId to toStopId.
     */
    public List<TransitPath> findPaths(TransitGraph graph, String fromStopId, String toStopId) {
        if (fromStopId.equals(toStopId)) return Collections.emptyList();
        if (graph.getStop(fromStopId) == null || graph.getStop(toStopId) == null) {
            return Collections.emptyList();
        }

        List<TransitPath> results = new ArrayList<>();
        Set<String> penalizedRoutes = new HashSet<>();

        for (int k = 0; k < K_PATHS; k++) {
            TransitPath path = runDijkstra(graph, fromStopId, toStopId, penalizedRoutes);
            if (path == null) break;

            results.add(path);
            penalizedRoutes.addAll(path.usedRouteIds());
        }

        return results;
    }

    private TransitPath runDijkstra(TransitGraph graph, String fromStopId, String toStopId,
                                    Set<String> penalizedRoutes) {
        PriorityQueue<DijkstraState> pq = new PriorityQueue<>(Comparator.comparingInt(s -> s.cost));
        Map<String, Integer> dist = new HashMap<>();

        String startKey = stateKey(fromStopId, null);
        dist.put(startKey, 0);
        pq.offer(new DijkstraState(0, fromStopId, null, 0, Collections.emptyList()));

        while (!pq.isEmpty()) {
            DijkstraState cur = pq.poll();
            String curKey = stateKey(cur.stopId, cur.lastBusRouteId);

            // Skip if we've already found a better cost for this state
            Integer bestCost = dist.get(curKey);
            if (bestCost != null && bestCost < cur.cost) continue;

            if (cur.stopId.equals(toStopId)) {
                return new TransitPath(cur.segments, cur.cost, cur.transfers);
            }

            for (TransitEdge edge : graph.getEdges(cur.stopId)) {
                int edgeCost = edge.weightMinutes();
                int newTransfers = cur.transfers;
                String newLastBusRouteId = cur.lastBusRouteId;
                int penalty = 0;

                if (edge.type() == EdgeType.BUS_RIDE) {
                    String edgeRouteId = edge.routeId();

                    // Transfer: boarding a bus different from the last one ridden
                    if (cur.lastBusRouteId != null && !cur.lastBusRouteId.equals(edgeRouteId)) {
                        newTransfers = cur.transfers + 1;
                        if (newTransfers > MAX_TRANSFERS) continue;
                        penalty = TRANSFER_PENALTY_MINUTES;
                    }
                    newLastBusRouteId = edgeRouteId;

                    // Penalize routes used in previous K-path iterations
                    if (penalizedRoutes.contains(edgeRouteId)) {
                        edgeCost += ROUTE_PENALTY_MINUTES;
                    }
                }
                // Walking: lastBusRouteId is preserved so the next bus boarding
                // correctly detects a transfer if the route changes.

                int newCost = cur.cost + edgeCost + penalty;
                String nextKey = stateKey(edge.toStopId(), newLastBusRouteId);

                if (!dist.containsKey(nextKey) || dist.get(nextKey) > newCost) {
                    dist.put(nextKey, newCost);

                    List<TransitPathSegment> newSegs = new ArrayList<>(cur.segments);
                    newSegs.add(new TransitPathSegment(
                            cur.stopId, edge.toStopId(),
                            edge.type(),
                            edge.weightMinutes(), // actual travel time, not penalized cost
                            edge.routeNumber(), edge.routeId()
                    ));
                    pq.offer(new DijkstraState(newCost, edge.toStopId(), newLastBusRouteId, newTransfers, newSegs));
                }
            }
        }

        return null; // No path found
    }

    private String stateKey(String stopId, String lastBusRouteId) {
        return stopId + "#" + (lastBusRouteId != null ? lastBusRouteId : "");
    }

    private record DijkstraState(
            int cost,
            String stopId,
            String lastBusRouteId, // null = no bus taken yet, or last move was walking
            int transfers,
            List<TransitPathSegment> segments
    ) {}
}
