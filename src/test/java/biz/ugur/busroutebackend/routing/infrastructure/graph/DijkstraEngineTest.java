package biz.ugur.busroutebackend.routing.infrastructure.graph;

import biz.ugur.busroutebackend.routing.domain.model.graph.EdgeType;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitEdge;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitGraph;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitPath;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitPathSegment;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


class DijkstraEngineTest {

    private static final String ROUTE1_ID = "route-1";
    private static final String ROUTE2_ID = "route-2";
    private static final String ROUTE3_ID = "route-3";

    private static final String A = "stop-A";
    private static final String B = "stop-B";
    private static final String C = "stop-C";
    private static final String D = "stop-D";
    private static final String E = "stop-E";
    private static final String F = "stop-F";

    private DijkstraEngine engine;
    private TransitGraph graph;

    @BeforeEach
    void setUp() {
        engine = new DijkstraEngine(new biz.ugur.busroutebackend.routing.infrastructure.config.DijkstraProperties());
        graph = buildTestGraph();
    }

    @Test
    void findPaths_directRoute_returnsOneBusSegment() {
        List<TransitPath> paths = engine.findPaths(graph, A, C);

        assertThat(paths).isNotEmpty();
        TransitPath best = paths.get(0);
        assertThat(best.busSegmentCount()).isEqualTo(1);
        List<TransitPathSegment> collapsed = best.collapsed();
        TransitPathSegment bus = collapsed.stream().filter(TransitPathSegment::isBusRide).findFirst().orElseThrow();
        assertThat(bus.fromStopId()).isEqualTo(A);
        assertThat(bus.toStopId()).isEqualTo(C);
        assertThat(bus.routeId()).isEqualTo(ROUTE1_ID);
    }

    @Test
    void findPaths_oneTransfer_correctTransferCount() {
        List<TransitPath> paths = engine.findPaths(graph, A, E);

        assertThat(paths).isNotEmpty();
        boolean found = paths.stream().anyMatch(p -> p.busSegmentCount() == 2);
        assertThat(found).isTrue();

        TransitPath twoSegPath = paths.stream()
                .filter(p -> p.busSegmentCount() == 2)
                .findFirst().orElseThrow();
        assertThat(twoSegPath.transfers()).isEqualTo(1);

        List<TransitPathSegment> collapsed = twoSegPath.collapsed();
        List<TransitPathSegment> busSegs = collapsed.stream()
                .filter(TransitPathSegment::isBusRide).toList();
        assertThat(busSegs.get(0).routeId()).isEqualTo(ROUTE1_ID);
        assertThat(busSegs.get(1).routeId()).isEqualTo(ROUTE2_ID);
    }

    @Test
    void findPaths_sameStop_returnsEmpty() {
        List<TransitPath> paths = engine.findPaths(graph, A, A);
        assertThat(paths).isEmpty();
    }

    @Test
    void findPaths_unknownStop_returnsEmpty() {
        List<TransitPath> paths = engine.findPaths(graph, A, "non-existent-stop");
        assertThat(paths).isEmpty();
    }

    @Test
    void findPaths_withWalking_findsAlternativePath() {
        List<TransitPath> paths = engine.findPaths(graph, A, E);
        assertThat(paths).isNotEmpty();
        assertThat(paths.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void findPaths_kPaths_returnsDifferentRoutes() {
        List<TransitPath> paths = engine.findPaths(graph, A, E);
        assertThat(paths.size()).isGreaterThanOrEqualTo(2);

        boolean hasR1Route = paths.stream()
                .flatMap(p -> p.usedRouteIds().stream())
                .anyMatch(ROUTE1_ID::equals);
        boolean hasR3Route = paths.stream()
                .flatMap(p -> p.usedRouteIds().stream())
                .anyMatch(ROUTE3_ID::equals);

        assertThat(hasR1Route).isTrue();
        assertThat(hasR3Route).isTrue();
    }

    @Test
    void findPaths_collapsed_mergesConsecutiveSameRouteSegments() {
        List<TransitPath> paths = engine.findPaths(graph, A, C);
        assertThat(paths).isNotEmpty();

        TransitPath path = paths.get(0);
        long rawBusHops = path.segments().stream().filter(TransitPathSegment::isBusRide).count();
        assertThat(rawBusHops).isEqualTo(2);

        List<TransitPathSegment> collapsed = path.collapsed();
        long collapsedBusSegs = collapsed.stream().filter(TransitPathSegment::isBusRide).count();
        assertThat(collapsedBusSegs).isEqualTo(1);
        TransitPathSegment merged = collapsed.stream().filter(TransitPathSegment::isBusRide).findFirst().orElseThrow();
        assertThat(merged.fromStopId()).isEqualTo(A);
        assertThat(merged.toStopId()).isEqualTo(C);
        assertThat(merged.costMinutes()).isEqualTo(4); 
    }


    private TransitGraph buildTestGraph() {
        Map<String, BusStop> stops = new HashMap<>();
        Map<String, BusRoute> routes = new HashMap<>();
        Map<String, List<TransitEdge>> adj = new HashMap<>();

        for (String id : List.of(A, B, C, D, E, F)) {
            stops.put(id, makeStop(id));
            adj.put(id, new ArrayList<>());
        }

        routes.put(ROUTE1_ID, makeRoute(ROUTE1_ID, "1"));
        routes.put(ROUTE2_ID, makeRoute(ROUTE2_ID, "2"));
        routes.put(ROUTE3_ID, makeRoute(ROUTE3_ID, "3"));

        adj.get(A).add(new TransitEdge(B, EdgeType.BUS_RIDE, 2, "1", ROUTE1_ID, 0));
        adj.get(B).add(new TransitEdge(C, EdgeType.BUS_RIDE, 2, "1", ROUTE1_ID, 0));

        adj.get(C).add(new TransitEdge(D, EdgeType.BUS_RIDE, 2, "2", ROUTE2_ID, 0));
        adj.get(D).add(new TransitEdge(E, EdgeType.BUS_RIDE, 2, "2", ROUTE2_ID, 0));

        adj.get(F).add(new TransitEdge(E, EdgeType.BUS_RIDE, 2, "3", ROUTE3_ID, 0));

        adj.get(A).add(new TransitEdge(F, EdgeType.WALKING, 3, null, null, null));
        adj.get(F).add(new TransitEdge(A, EdgeType.WALKING, 3, null, null, null));

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
