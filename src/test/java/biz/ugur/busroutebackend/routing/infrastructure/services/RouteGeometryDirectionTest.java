package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RouteGeometryDirectionTest {

    private RouteGeometryTrimmingService heuristic;

    private static final String FORWARD_WKT =
            "LINESTRING(58.350 37.950, 58.360 37.950, 58.370 37.950)";
    private static final String BACKWARD_WKT =
            "LINESTRING(58.370 37.951, 58.360 37.951, 58.350 37.951)";

    @BeforeEach
    void setUp() {
        heuristic = new RouteGeometryTrimmingService(new DistanceCalculationService());
    }

    private BusStop stop(String name, double lat, double lon) {
        return BusStop.create(name, name, name,
                StopCode.of(name + "-CODE"),
                BigDecimal.valueOf(lat), BigDecimal.valueOf(lon),
                false, "city-001", "admin");
    }

    @Nested
    class AuthoritativeDirection {

        @Test
        void direction0SelectsForwardEvenWhenBothGeometriesValid() {
            BusStop from = stop("A", 37.9505, 58.355);
            BusStop to = stop("B", 37.9505, 58.365);

            String chosen = RouteGeometrySelector.select(
                    FORWARD_WKT, BACKWARD_WKT, 0, from, to, heuristic);

            assertThat(chosen).isEqualTo(FORWARD_WKT);
        }

        /**
         * Regression test for Bug B: bus-segment polyline rendered on the
         * opposite lane (forward when the trip is going backward). Original
         * fix: commit a924c6c made direction propagate authoritatively
         * through {@code TransitPathSegment} all the way to
         * {@code RouteGeometrySelector.select}. Smoke-checked 2026-04-29:
         * commenting out the {@code if (direction != null)} block in
         * {@code RouteGeometrySelector.select} causes this test to fail.
         *
         * <p>See {@code docs/DIJKSTRA_AUDIT_REPORT.md §3}.
         */
        @Test
        void direction1SelectsBackwardEvenWhenForwardHeuristicWouldPass() {
            BusStop from = stop("A", 37.9505, 58.355);
            BusStop to = stop("B", 37.9505, 58.365);

            String chosen = RouteGeometrySelector.select(
                    FORWARD_WKT, BACKWARD_WKT, 1, from, to, heuristic);

            assertThat(chosen).isEqualTo(BACKWARD_WKT);
        }

        /**
         * Regression test for Bug B specifically on Ashgabat frontage-road
         * pattern: a setup where the legacy heuristic
         * {@code RouteGeometryTrimmingService.selectGeometryForDirection}
         * would project both forward and backward polylines and pick
         * {@code FORWARD_WKT} — but the authoritative {@code direction=1}
         * must override and return {@code BACKWARD_WKT}. Smoke-checked
         * 2026-04-29: disabling the authoritative path makes this test
         * fail.
         *
         * <p>See {@code docs/DIJKSTRA_AUDIT_REPORT.md §3.4}.
         */
        @Test
        void authoritativeDirectionWinsOverHeuristicOnFrontageRoadPattern() {
            BusStop from = stop("A", 37.9505, 58.355);
            BusStop to = stop("B", 37.9505, 58.365);

            String heuristicChoice = heuristic.selectGeometryForDirection(
                    FORWARD_WKT, BACKWARD_WKT, from, to);
            assertThat(heuristicChoice).isEqualTo(FORWARD_WKT);

            String authoritativeChoice = RouteGeometrySelector.select(
                    FORWARD_WKT, BACKWARD_WKT, 1, from, to, heuristic);
            assertThat(authoritativeChoice).isEqualTo(BACKWARD_WKT);
        }
    }

    @Nested
    class Fallbacks {

        /**
         * Regression test for the SQL-fallback path: when the search
         * pipeline ends up running through {@code GraphRouteCalculationService}
         * (Dijkstra disabled), result records carry {@code direction=null}
         * and the deprecated heuristic is the only thing keeping the
         * polyline on the right lane. If a future cleanup removes the
         * heuristic, this test will fail and force a conscious decision.
         * Smoke-checked 2026-04-29: short-circuiting the heuristic call
         * in {@code RouteGeometrySelector.select} (returning null instead)
         * causes this test to fail.
         *
         * <p>See {@code docs/DIJKSTRA_AUDIT_REPORT.md §3.2 line 10},
         * {@code §8.4} (planned heuristic removal as backlog).
         */
        @Test
        void nullDirectionFallsBackToHeuristic() {
            BusStop from = stop("A", 37.9505, 58.355);
            BusStop to = stop("B", 37.9505, 58.365);

            String chosen = RouteGeometrySelector.select(
                    FORWARD_WKT, BACKWARD_WKT, null, from, to, heuristic);

            assertThat(chosen).isEqualTo(FORWARD_WKT);
        }

        @Test
        void direction1FallsBackToForwardWhenBackwardMissing() {
            BusStop from = stop("A", 37.9505, 58.355);
            BusStop to = stop("B", 37.9505, 58.365);

            String chosen = RouteGeometrySelector.select(
                    FORWARD_WKT, null, 1, from, to, heuristic);

            assertThat(chosen).isEqualTo(FORWARD_WKT);
        }

        @Test
        void direction0FallsBackToBackwardWhenForwardMissing() {
            BusStop from = stop("A", 37.9505, 58.355);
            BusStop to = stop("B", 37.9505, 58.365);

            String chosen = RouteGeometrySelector.select(
                    null, BACKWARD_WKT, 0, from, to, heuristic);

            assertThat(chosen).isEqualTo(BACKWARD_WKT);
        }

        @Test
        void bothNullReturnsNull() {
            BusStop from = stop("A", 37.9505, 58.355);
            BusStop to = stop("B", 37.9505, 58.365);

            String chosen = RouteGeometrySelector.select(
                    null, null, 0, from, to, heuristic);

            assertThat(chosen).isNull();
        }
    }
}
