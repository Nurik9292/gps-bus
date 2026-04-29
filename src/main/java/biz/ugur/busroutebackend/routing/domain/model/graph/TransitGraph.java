package biz.ugur.busroutebackend.routing.domain.model.graph;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TransitGraph {

    private final Map<String, BusStop> stops;
    private final Map<String, BusRoute> routes;
    private final Map<String, List<TransitEdge>> adj;
    private final Map<String, Set<String>> busRouteIdsAtStop;
    private final LocalDateTime builtAt;
    private final int stopCount;
    private final int edgeCount;

    public TransitGraph(Map<String, BusStop> stops,
                        Map<String, BusRoute> routes,
                        Map<String, List<TransitEdge>> adj,
                        Map<String, Set<String>> busRouteIdsAtStop) {
        this.stops = stops;
        this.routes = routes;
        this.adj = adj;
        this.busRouteIdsAtStop = busRouteIdsAtStop;
        this.builtAt = LocalDateTime.now();
        this.stopCount = stops.size();
        this.edgeCount = adj.values().stream().mapToInt(List::size).sum();
    }

    public TransitGraph(Map<String, BusStop> stops,
                        Map<String, BusRoute> routes,
                        Map<String, List<TransitEdge>> adj) {
        this(stops, routes, adj, deriveBusRouteIdsAtStop(adj));
    }

    private static Map<String, Set<String>> deriveBusRouteIdsAtStop(Map<String, List<TransitEdge>> adj) {
        Map<String, Set<String>> result = new java.util.HashMap<>();
        for (Map.Entry<String, List<TransitEdge>> e : adj.entrySet()) {
            for (TransitEdge edge : e.getValue()) {
                if (edge.isBusRide() && edge.routeId() != null) {
                    result.computeIfAbsent(e.getKey(), k -> new java.util.HashSet<>()).add(edge.routeId());
                    result.computeIfAbsent(edge.toStopId(), k -> new java.util.HashSet<>()).add(edge.routeId());
                }
            }
        }
        return result;
    }

    public List<TransitEdge> getEdges(String stopId) {
        return adj.getOrDefault(stopId, Collections.emptyList());
    }

    public BusStop getStop(String stopId) {
        return stops.get(stopId);
    }

    public BusRoute getRoute(String routeId) {
        return routes.get(routeId);
    }

    public Set<String> getBusRouteIdsAtStop(String stopId) {
        return busRouteIdsAtStop.getOrDefault(stopId, Collections.emptySet());
    }

    public boolean isExpired(Duration ttl) {
        return LocalDateTime.now().isAfter(builtAt.plus(ttl));
    }

    public int getStopCount() { return stopCount; }
    public int getEdgeCount() { return edgeCount; }
    public LocalDateTime getBuiltAt() { return builtAt; }
}
