package biz.ugur.busroutebackend.routing.infrastructure.graph;

import biz.ugur.busroutebackend.routing.domain.model.graph.EdgeType;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitEdge;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitGraph;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitPath;
import biz.ugur.busroutebackend.routing.infrastructure.config.DijkstraProperties;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DijkstraEnginePerformanceTest {

    private static final String LONG_ROUTE = "perf-route";

    @Test
    void reachableLongPath_findsResultUnder5000Iterations() {
        DijkstraProperties props = new DijkstraProperties();
        props.setMaxIterations(5000);
        props.setMaxCostMinutes(600);
        DijkstraEngine engine = new DijkstraEngine(props);

        TransitGraph graph = buildLinearChainGraph(500);

        List<TransitPath> paths = engine.findPaths(graph, "stop-0", "stop-499", 1);

        assertThat(paths)
                .as("Reachable end-to-end path through 500-stop chain must be found within 5000 iterations")
                .isNotEmpty();
        assertThat(paths.get(0).busSegmentCount()).isEqualTo(1);
    }

    @Test
    void unreachablePair_returnsEmptyWithoutExceedingIterations() {
        DijkstraProperties props = new DijkstraProperties();
        props.setMaxIterations(10_000);
        DijkstraEngine engine = new DijkstraEngine(props);

        TransitGraph graph = buildDisconnectedGraph();

        List<TransitPath> paths = engine.findPaths(graph, "stop-A1", "stop-B1", 3);

        assertThat(paths)
                .as("Disconnected graph: pair on different components must return empty without burning iterations")
                .isEmpty();
    }

    private TransitGraph buildLinearChainGraph(int stopCount) {
        Map<String, BusStop> stops = new HashMap<>();
        Map<String, BusRoute> routes = new HashMap<>();
        Map<String, List<TransitEdge>> adj = new HashMap<>();

        routes.put(LONG_ROUTE, makeRoute(LONG_ROUTE, "L"));

        for (int i = 0; i < stopCount; i++) {
            String id = "stop-" + i;
            stops.put(id, makeStop(id));
            adj.put(id, new ArrayList<>());
        }
        for (int i = 0; i < stopCount - 1; i++) {
            adj.get("stop-" + i).add(
                    new TransitEdge("stop-" + (i + 1), EdgeType.BUS_RIDE, 1, "L", LONG_ROUTE, 0));
        }

        return new TransitGraph(stops, routes, adj);
    }

    private TransitGraph buildDisconnectedGraph() {
        Map<String, BusStop> stops = new HashMap<>();
        Map<String, BusRoute> routes = new HashMap<>();
        Map<String, List<TransitEdge>> adj = new HashMap<>();

        routes.put("rA", makeRoute("rA", "A"));
        routes.put("rB", makeRoute("rB", "B"));

        for (String id : List.of("stop-A1", "stop-A2", "stop-A3")) {
            stops.put(id, makeStop(id));
            adj.put(id, new ArrayList<>());
        }
        adj.get("stop-A1").add(new TransitEdge("stop-A2", EdgeType.BUS_RIDE, 1, "A", "rA", 0));
        adj.get("stop-A2").add(new TransitEdge("stop-A3", EdgeType.BUS_RIDE, 1, "A", "rA", 0));

        for (String id : List.of("stop-B1", "stop-B2", "stop-B3")) {
            stops.put(id, makeStop(id));
            adj.put(id, new ArrayList<>());
        }
        adj.get("stop-B1").add(new TransitEdge("stop-B2", EdgeType.BUS_RIDE, 1, "B", "rB", 0));
        adj.get("stop-B2").add(new TransitEdge("stop-B3", EdgeType.BUS_RIDE, 1, "B", "rB", 0));

        return new TransitGraph(stops, routes, adj);
    }

    private BusStop makeStop(String id) {
        return BusStop.restore(
                BusStopId.of(id), id, null, null, null,
                BigDecimal.valueOf(37.9), BigDecimal.valueOf(58.4),
                true, false, null, null, null, 0L
        );
    }

    private BusRoute makeRoute(String id, String number) {
        return BusRoute.restore(
                BusRouteId.of(id), number, "Route " + number,
                null, null, "#1976D2",
                true, null, 20,
                null, null, null, null,
                null, null, 0L
        );
    }
}
