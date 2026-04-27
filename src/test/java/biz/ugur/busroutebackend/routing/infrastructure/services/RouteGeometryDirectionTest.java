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

        @Test
        void direction1SelectsBackwardEvenWhenForwardHeuristicWouldPass() {
            BusStop from = stop("A", 37.9505, 58.355);
            BusStop to = stop("B", 37.9505, 58.365);

            String chosen = RouteGeometrySelector.select(
                    FORWARD_WKT, BACKWARD_WKT, 1, from, to, heuristic);

            assertThat(chosen).isEqualTo(BACKWARD_WKT);
        }

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
