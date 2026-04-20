package biz.ugur.busroutebackend.transport.domain.specification;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BusRouteSpecificationsTest {

    private BusRoute active() {
        return BusRoute.create("29A", "Main Street", "Esasy", "Main", "#FF5722", "ashgabat", 45);
    }

    private BusRoute activeWithGeometry() {
        RouteGeometry fwd = new RouteGeometry(List.of(
                Coordinates.of(37.96, 58.32), Coordinates.of(37.97, 58.33)));
        RouteGeometry bwd = new RouteGeometry(List.of(
                Coordinates.of(37.97, 58.33), Coordinates.of(37.96, 58.32)));
        return active().updateRouteGeometry(fwd, bwd);
    }

    @Nested
    class StatusAndIdentity {

        @Test
        void isActiveMatchesActive() {
            assertTrue(BusRouteSpecifications.isActive().isSatisfiedBy(active()));
            assertFalse(BusRouteSpecifications.isActive().isSatisfiedBy(active().deactivate()));
        }

        @Test
        void isInactiveMatchesInactive() {
            assertTrue(BusRouteSpecifications.isInactive().isSatisfiedBy(active().deactivate()));
            assertFalse(BusRouteSpecifications.isInactive().isSatisfiedBy(active()));
        }

        @Test
        void hasIdMatchesOnRouteId() {
            BusRoute route = active();
            assertTrue(BusRouteSpecifications.hasId(route.getId().getValue()).isSatisfiedBy(route));
            assertFalse(BusRouteSpecifications.hasId("nope").isSatisfiedBy(route));
        }

        @Test
        void hasColorIsCaseInsensitive() {
            assertTrue(BusRouteSpecifications.hasColor("#ff5722").isSatisfiedBy(active()));
            assertFalse(BusRouteSpecifications.hasColor("#000000").isSatisfiedBy(active()));
        }

        @Test
        void servesCityMatchesCityId() {
            assertTrue(BusRouteSpecifications.servesCity("ashgabat").isSatisfiedBy(active()));
            assertFalse(BusRouteSpecifications.servesCity("mary").isSatisfiedBy(active()));
        }
    }

    @Nested
    class Geometry {

        @Test
        void hasGeometryFalseForFreshRoute() {
            assertFalse(BusRouteSpecifications.hasGeometry().isSatisfiedBy(active()));
        }

        @Test
        void hasGeometryTrueForRouteWithBothDirections() {
            assertTrue(BusRouteSpecifications.hasGeometry().isSatisfiedBy(activeWithGeometry()));
        }

        @Test
        void hasForwardGeometryTrueForForwardOnly() {
            BusRoute fwdOnly = active().updateForwardGeometry("LINESTRING(58 37, 58 38)", 100);
            assertTrue(BusRouteSpecifications.hasForwardGeometry().isSatisfiedBy(fwdOnly));
            assertFalse(BusRouteSpecifications.hasBackwardGeometry().isSatisfiedBy(fwdOnly));
        }

        @Test
        void hasCompleteGeometryRequiresBothDirections() {
            BusRoute fwdOnly = active().updateForwardGeometry("LINESTRING(58 37, 58 38)", 100);
            assertFalse(BusRouteSpecifications.hasCompleteGeometry().isSatisfiedBy(fwdOnly));
            assertTrue(BusRouteSpecifications.hasCompleteGeometry().isSatisfiedBy(activeWithGeometry()));
        }
    }

    @Nested
    class TextMatching {

        @Test
        void hasRouteNumberCaseInsensitive() {
            assertTrue(BusRouteSpecifications.hasRouteNumber("29a").isSatisfiedBy(active()));
            assertFalse(BusRouteSpecifications.hasRouteNumber("30").isSatisfiedBy(active()));
        }

        @Test
        void nameContainsMatchesSubstring() {
            assertTrue(BusRouteSpecifications.nameContains("main").isSatisfiedBy(active()));
            assertFalse(BusRouteSpecifications.nameContains("xyz").isSatisfiedBy(active()));
        }

        @Test
        void routeNumberContainsMatchesFragment() {
            assertTrue(BusRouteSpecifications.routeNumberContains("29").isSatisfiedBy(active()));
            assertFalse(BusRouteSpecifications.routeNumberContains("ZZ").isSatisfiedBy(active()));
        }
    }

    @Nested
    class Translations {

        @Test
        void hasEnglishTranslationRequiresNonBlank() {
            assertTrue(BusRouteSpecifications.hasEnglishTranslation().isSatisfiedBy(active()));
            BusRoute noEn = BusRoute.create("1", "Name", "Name", "", "#FF5722", "c", 10);
            assertFalse(BusRouteSpecifications.hasEnglishTranslation().isSatisfiedBy(noEn));
        }

        @Test
        void hasTurkmenTranslationRequiresNonBlank() {
            assertTrue(BusRouteSpecifications.hasTurkmenTranslation().isSatisfiedBy(active()));
            BusRoute noTm = BusRoute.create("1", "Name", "", "Name", "#FF5722", "c", 10);
            assertFalse(BusRouteSpecifications.hasTurkmenTranslation().isSatisfiedBy(noTm));
        }
    }

    @Nested
    class Metrics {

        @Test
        void distanceAboveMatchesWhenEitherDirectionOverThreshold() {
            BusRoute withDistance = active()
                    .updateForwardGeometry("LINESTRING(0 0, 0 0)", 15000)
                    .updateBackwardGeometry("LINESTRING(0 0, 0 0)", 8000);
            assertTrue(BusRouteSpecifications.distanceAbove(10000).isSatisfiedBy(withDistance));
            assertFalse(BusRouteSpecifications.distanceAbove(20000).isSatisfiedBy(withDistance));
        }

        @Test
        void durationAboveMatchesWhenThresholdBeaten() {
            assertTrue(BusRouteSpecifications.durationAbove(30).isSatisfiedBy(active()));
            assertFalse(BusRouteSpecifications.durationAbove(60).isSatisfiedBy(active()));
        }

        @Test
        void isShortDistanceMatchesWhenAnyDirectionBelow5km() {
            BusRoute shortRoute = active()
                    .updateForwardGeometry("LINESTRING(0 0, 0 0)", 2000)
                    .updateBackwardGeometry("LINESTRING(0 0, 0 0)", 2000);
            assertTrue(BusRouteSpecifications.isShortDistance().isSatisfiedBy(shortRoute));

            BusRoute longRoute = active()
                    .updateForwardGeometry("LINESTRING(0 0, 0 0)", 20000)
                    .updateBackwardGeometry("LINESTRING(0 0, 0 0)", 20000);
            assertFalse(BusRouteSpecifications.isShortDistance().isSatisfiedBy(longRoute));
        }
    }

    @Nested
    class Composite {

        @Test
        void isReadyForServiceRequiresActiveAndCompleteGeometry() {
            assertTrue(BusRouteSpecifications.isReadyForService().isSatisfiedBy(activeWithGeometry()));
            assertFalse(BusRouteSpecifications.isReadyForService().isSatisfiedBy(active()));
        }

        @Test
        void requiresGeometryUpdateForActiveWithoutGeometry() {
            assertTrue(BusRouteSpecifications.requiresGeometryUpdate().isSatisfiedBy(active()));
            assertFalse(BusRouteSpecifications.requiresGeometryUpdate().isSatisfiedBy(activeWithGeometry()));
        }

        @Test
        void isFullyConfiguredRequiresActiveGeometryAndTranslations() {
            assertTrue(BusRouteSpecifications.isFullyConfigured().isSatisfiedBy(activeWithGeometry()));
            BusRoute noTm = BusRoute.create("1", "Name", "", "Name", "#FF5722", "c", 10)
                    .updateForwardGeometry("LINESTRING(0 0, 0 0)", 100)
                    .updateBackwardGeometry("LINESTRING(0 0, 0 0)", 100);
            assertFalse(BusRouteSpecifications.isFullyConfigured().isSatisfiedBy(noTm));
        }

        @Test
        void isLongDistanceRequiresActiveAndDistanceAbove10km() {
            BusRoute longRoute = active()
                    .updateForwardGeometry("LINESTRING(0 0, 0 0)", 15000)
                    .updateBackwardGeometry("LINESTRING(0 0, 0 0)", 15000);
            assertTrue(BusRouteSpecifications.isLongDistance().isSatisfiedBy(longRoute));
        }
    }

    @Nested
    class SqlCriteriaGeneration {

        @Test
        void isActiveProducesBooleanParameter() {
            Specification<BusRoute> spec = BusRouteSpecifications.isActive();
            assertTrue(spec.toSqlCriteria().getWhereClause().contains("is_active"));
            assertEquals(true, spec.toSqlCriteria().getParameters().get("isActive"));
        }

        @Test
        void hasGeometrySqlCombinesDirections() {
            String clause = BusRouteSpecifications.hasGeometry().toSqlCriteria().getWhereClause();
            assertTrue(clause.contains("route_geometry_forward"));
            assertTrue(clause.contains("route_geometry_backward"));
        }

        @Test
        void servesCityBindsCityId() {
            assertEquals("mary",
                    BusRouteSpecifications.servesCity("mary").toSqlCriteria().getParameters().get("cityId"));
        }
    }
}
