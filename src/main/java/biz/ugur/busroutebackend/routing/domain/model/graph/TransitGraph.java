package biz.ugur.busroutebackend.routing.domain.model.graph;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TransitGraph {

    private final Map<String, BusStop> stops;      
    private final Map<String, BusRoute> routes;    
    private final Map<String, List<TransitEdge>> adj; 
    private final LocalDateTime builtAt;
    private final int stopCount;
    private final int edgeCount;

    public TransitGraph(Map<String, BusStop> stops,
                        Map<String, BusRoute> routes,
                        Map<String, List<TransitEdge>> adj) {
        this.stops = stops;
        this.routes = routes;
        this.adj = adj;
        this.builtAt = LocalDateTime.now();
        this.stopCount = stops.size();
        this.edgeCount = adj.values().stream().mapToInt(List::size).sum();
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

    public boolean isExpired(Duration ttl) {
        return LocalDateTime.now().isAfter(builtAt.plus(ttl));
    }

    public int getStopCount() { return stopCount; }
    public int getEdgeCount() { return edgeCount; }
    public LocalDateTime getBuiltAt() { return builtAt; }
}
