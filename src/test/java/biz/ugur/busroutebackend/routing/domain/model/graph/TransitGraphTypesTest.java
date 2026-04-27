package biz.ugur.busroutebackend.routing.domain.model.graph;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TransitGraphTypesTest {

    @Nested
    class TransitEdgeTests {

        @Test
        void busEdgeIdentifiesItself() {
            TransitEdge e = new TransitEdge("stop-B", EdgeType.BUS_RIDE, 5, "29A", "route-1", 0);
            assertTrue(e.isBusRide());
            assertFalse(e.isWalking());
            assertEquals("stop-B", e.toStopId());
            assertEquals(5, e.weightMinutes());
        }

        @Test
        void walkingEdgeIdentifiesItself() {
            TransitEdge e = new TransitEdge("stop-B", EdgeType.WALKING, 2, null, null, null);
            assertTrue(e.isWalking());
            assertFalse(e.isBusRide());
        }
    }

    @Nested
    class TransitPathSegmentTests {

        @Test
        void busRideSegmentDetectsType() {
            TransitPathSegment s = new TransitPathSegment(
                    "A", "B", EdgeType.BUS_RIDE, 10, "29A", "route-1", 0);
            assertTrue(s.isBusRide());
            assertFalse(s.isWalking());
        }

        @Test
        void walkingSegmentDetectsType() {
            TransitPathSegment s = new TransitPathSegment(
                    "A", "B", EdgeType.WALKING, 3, null, null, null);
            assertTrue(s.isWalking());
            assertFalse(s.isBusRide());
        }
    }

    @Nested
    class TransitPathTests {

        @Test
        void collapsedMergesConsecutiveBusRidesOnSameRoute() {
            TransitPath path = new TransitPath(List.of(
                    new TransitPathSegment("A", "B", EdgeType.BUS_RIDE, 5, "29A", "r1", 0),
                    new TransitPathSegment("B", "C", EdgeType.BUS_RIDE, 4, "29A", "r1", 0),
                    new TransitPathSegment("C", "D", EdgeType.BUS_RIDE, 3, "29A", "r1", 0)
            ), 12, 0);

            List<TransitPathSegment> collapsed = path.collapsed();
            assertEquals(1, collapsed.size());
            assertEquals("A", collapsed.get(0).fromStopId());
            assertEquals("D", collapsed.get(0).toStopId());
            assertEquals(12, collapsed.get(0).costMinutes());
        }

        @Test
        void collapsedKeepsSegmentsOnDifferentRoutes() {
            TransitPath path = new TransitPath(List.of(
                    new TransitPathSegment("A", "B", EdgeType.BUS_RIDE, 5, "1", "r1", 0),
                    new TransitPathSegment("B", "C", EdgeType.BUS_RIDE, 3, "2", "r2", 0)
            ), 8, 1);

            List<TransitPathSegment> collapsed = path.collapsed();
            assertEquals(2, collapsed.size());
        }

        @Test
        void collapsedKeepsSegmentsOnSameRouteButDifferentDirection() {
            TransitPath path = new TransitPath(List.of(
                    new TransitPathSegment("A", "B", EdgeType.BUS_RIDE, 5, "29A", "r1", 0),
                    new TransitPathSegment("B", "C", EdgeType.BUS_RIDE, 4, "29A", "r1", 1)
            ), 9, 0);

            List<TransitPathSegment> collapsed = path.collapsed();
            assertEquals(2, collapsed.size());
        }

        @Test
        void collapsedKeepsWalkingSegmentsSeparate() {
            TransitPath path = new TransitPath(List.of(
                    new TransitPathSegment("A", "B", EdgeType.WALKING, 3, null, null, null),
                    new TransitPathSegment("B", "C", EdgeType.WALKING, 2, null, null, null)
            ), 5, 0);
            assertEquals(2, path.collapsed().size());
        }

        @Test
        void collapsedEmptyReturnsEmpty() {
            TransitPath path = new TransitPath(List.of(), 0, 0);
            assertEquals(0, path.collapsed().size());
        }

        @Test
        void busSegmentCountReflectsCollapsed() {
            TransitPath path = new TransitPath(List.of(
                    new TransitPathSegment("A", "B", EdgeType.BUS_RIDE, 5, "29A", "r1", 0),
                    new TransitPathSegment("B", "C", EdgeType.BUS_RIDE, 4, "29A", "r1", 0),
                    new TransitPathSegment("C", "D", EdgeType.WALKING, 2, null, null, null),
                    new TransitPathSegment("D", "E", EdgeType.BUS_RIDE, 5, "2", "r2", 0)
            ), 16, 1);

            assertEquals(2, path.busSegmentCount());
        }

        @Test
        void usedRouteIdsDeduplicates() {
            TransitPath path = new TransitPath(List.of(
                    new TransitPathSegment("A", "B", EdgeType.BUS_RIDE, 5, "29A", "r1", 0),
                    new TransitPathSegment("B", "C", EdgeType.BUS_RIDE, 3, "29A", "r1", 0),
                    new TransitPathSegment("C", "D", EdgeType.BUS_RIDE, 4, "2", "r2", 0)
            ), 12, 1);

            Set<String> ids = path.usedRouteIds();
            assertEquals(2, ids.size());
            assertTrue(ids.contains("r1"));
            assertTrue(ids.contains("r2"));
        }

        @Test
        void usedRouteIdsSkipsNullsAndWalking() {
            TransitPath path = new TransitPath(List.of(
                    new TransitPathSegment("A", "B", EdgeType.BUS_RIDE, 5, "29A", null, 0),
                    new TransitPathSegment("B", "C", EdgeType.WALKING, 3, null, null, null)
            ), 8, 0);
            assertEquals(0, path.usedRouteIds().size());
        }
    }

    @Nested
    class TransitGraphTests {

        private final BusStop stopA = BusStop.create("A", "A", "A",
                StopCode.of("ASH001"),
                new BigDecimal("37.9601"), new BigDecimal("58.3261"),
                false, "city-001", "admin");
        private final BusStop stopB = BusStop.create("B", "B", "B",
                StopCode.of("ASH002"),
                new BigDecimal("37.9701"), new BigDecimal("58.3361"),
                false, "city-001", "admin");
        private final BusRoute route = BusRoute.create("1", "Route 1", "", "",
                "#FF5722", "city-001", 20);

        @Test
        void graphExposesStopsRoutesAndEdges() {
            TransitEdge edge = new TransitEdge(stopB.getId().getValue(), EdgeType.BUS_RIDE, 5, "1",
                    route.getId().getValue(), 0);
            TransitGraph graph = new TransitGraph(
                    Map.of(stopA.getId().getValue(), stopA, stopB.getId().getValue(), stopB),
                    Map.of(route.getId().getValue(), route),
                    Map.of(stopA.getId().getValue(), List.of(edge))
            );

            assertEquals(2, graph.getStopCount());
            assertEquals(1, graph.getEdgeCount());
            assertEquals(stopA, graph.getStop(stopA.getId().getValue()));
            assertEquals(route, graph.getRoute(route.getId().getValue()));
            assertEquals(1, graph.getEdges(stopA.getId().getValue()).size());
        }

        @Test
        void getEdgesReturnsEmptyForUnknownStop() {
            TransitGraph graph = new TransitGraph(Map.of(), Map.of(), Map.of());
            assertTrue(graph.getEdges("missing").isEmpty());
        }

        @Test
        void isExpiredFalseImmediatelyAfterBuild() {
            TransitGraph graph = new TransitGraph(Map.of(), Map.of(), Map.of());
            assertFalse(graph.isExpired(Duration.ofMinutes(30)));
            assertNotNull(graph.getBuiltAt());
        }

        @Test
        void isExpiredTrueForZeroTtl() {
            TransitGraph graph = new TransitGraph(Map.of(), Map.of(), Map.of());
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            assertTrue(graph.isExpired(Duration.ZERO));
        }
    }
}
