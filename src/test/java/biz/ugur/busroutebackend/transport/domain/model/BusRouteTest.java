package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.enums.RouteDirection;
import biz.ugur.busroutebackend.transport.domain.event.RouteGeometryUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.exceptions.RouteValidationException;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BusRouteTest {

    private BusRoute fresh;

    @BeforeEach
    void setUp() {
        fresh = BusRoute.create("29A", "Mollanepes - Berzengi", "Mollanepes-Berzengi", "Mollanepes to Berzengi",
                "#FF5722", "ashgabat", 45);
    }

    @Nested
    class Create {

        @Test
        void shouldCreateActiveRouteWithNormalisedFields() {
            assertNotNull(fresh.getId());
            assertEquals("29A", fresh.getRouteNumber());
            assertEquals("Mollanepes - Berzengi", fresh.getRouteName());
            assertEquals("Mollanepes-Berzengi", fresh.getNameTm());
            assertEquals("Mollanepes to Berzengi", fresh.getNameEn());
            assertEquals("#FF5722", fresh.getRouteColor());
            assertTrue(fresh.getIsActive());
            assertEquals("ashgabat", fresh.getCityId());
            assertEquals(45, fresh.getEstimatedDurationMinutes());
            assertFalse(fresh.hasGeometry());
            assertEquals(0L, fresh.getVersion());
        }

        @Test
        void shouldUppercaseRouteNumberAndColor() {
            BusRoute r = BusRoute.create("7al", "Name", "", "", "#abcdef", "city", 30);
            assertEquals("7AL", r.getRouteNumber());
            assertEquals("#ABCDEF", r.getRouteColor());
        }

        @Test
        void shouldFallBackToDefaultColorForInvalidColor() {
            BusRoute r = BusRoute.create("1", "Name", "", "", "not-a-color", "city", 10);
            assertEquals("#1976D2", r.getRouteColor());
        }

        @Test
        void shouldFallBackToDefaultColorForNullColor() {
            BusRoute r = BusRoute.create("1", "Name", "", "", null, "city", 10);
            assertEquals("#1976D2", r.getRouteColor());
        }

        @Test
        void shouldRejectBlankRouteNumber() {
            assertThrows(RouteValidationException.class,
                    () -> BusRoute.create("  ", "Name", "", "", "#FF5722", "city", 10));
        }

        @Test
        void shouldRejectMalformedRouteNumber() {
            RouteValidationException ex = assertThrows(RouteValidationException.class,
                    () -> BusRoute.create("!@#", "Name", "", "", "#FF5722", "city", 10));
            assertTrue(ex.getMessage().contains("route number"));
        }

        @Test
        void shouldRejectBlankRouteName() {
            assertThrows(RouteValidationException.class,
                    () -> BusRoute.create("1", "   ", "", "", "#FF5722", "city", 10));
        }

        @Test
        void shouldTrimTmAndEnNames() {
            BusRoute r = BusRoute.create("1", "Name", "  tm ", "  en  ", "#FF5722", "city", 10);
            assertEquals("tm", r.getNameTm());
            assertEquals("en", r.getNameEn());
        }

        @Test
        void shouldReplaceNullTmOrEnWithEmpty() {
            BusRoute r = BusRoute.create("1", "Name", null, null, "#FF5722", "city", 10);
            assertEquals("", r.getNameTm());
            assertEquals("", r.getNameEn());
        }
    }

    @Nested
    class UpdateInfo {

        @Test
        void updateBasicInfoReplacesFields() {
            BusRoute updated = fresh.updateBasicInfo("30B", "New name", "tm", "en", "#00FF00", 60, "mary");
            assertEquals("30B", updated.getRouteNumber());
            assertEquals("New name", updated.getRouteName());
            assertEquals("tm", updated.getNameTm());
            assertEquals("en", updated.getNameEn());
            assertEquals("#00FF00", updated.getRouteColor());
            assertEquals(60, updated.getEstimatedDurationMinutes());
            assertEquals("mary", updated.getCityId());
        }

        @Test
        void updateBasicInfoStillValidates() {
            assertThrows(RouteValidationException.class,
                    () -> fresh.updateBasicInfo(null, "name", "", "", "#FF0000", 30, "c"));
        }
    }

    @Nested
    class Geometry {

        @Test
        void hasGeometryFalseInitially() {
            assertFalse(fresh.hasGeometry());
            assertFalse(fresh.hasForwardGeometry());
            assertFalse(fresh.hasBackwardGeometry());
            assertFalse(fresh.hasCompleteGeometry());
            assertEquals(0, fresh.getTotalGeometryPoints());
            assertNull(fresh.getForwardGeometry());
            assertNull(fresh.getBackwardGeometry());
        }

        @Test
        void updateRouteGeometryStoresWKTAndDistances() {
            RouteGeometry fwd = new RouteGeometry(List.of(
                    Coordinates.of(37.9601, 58.3261),
                    Coordinates.of(37.9701, 58.3361)));
            RouteGeometry bwd = new RouteGeometry(List.of(
                    Coordinates.of(37.9701, 58.3361),
                    Coordinates.of(37.9601, 58.3261)));

            BusRoute updated = fresh.updateRouteGeometry(fwd, bwd);

            assertTrue(updated.hasForwardGeometry());
            assertTrue(updated.hasBackwardGeometry());
            assertTrue(updated.hasCompleteGeometry());
            assertTrue(updated.getTotalDistanceForwardMeters() > 0);
            assertTrue(updated.getTotalDistanceBackwardMeters() > 0);
            assertEquals(4, updated.getTotalGeometryPoints());
            assertEquals(1, updated.getDomainEvents().size());
            assertInstanceOf(RouteGeometryUpdatedEvent.class, updated.getDomainEvents().get(0));
        }

        @Test
        void updateRouteGeometryAllowsPartialUpdate() {
            RouteGeometry fwd = new RouteGeometry(List.of(
                    Coordinates.of(37.96, 58.32),
                    Coordinates.of(37.97, 58.33)));
            BusRoute updated = fresh.updateRouteGeometry(fwd, null);
            assertTrue(updated.hasForwardGeometry());
            assertFalse(updated.hasBackwardGeometry());
            assertEquals(1, updated.getDomainEvents().size());
        }

        @Test
        void updateRouteGeometryWithBothNullDoesNotEmitEvent() {
            BusRoute updated = fresh.updateRouteGeometry(null, null);
            assertFalse(updated.hasGeometry());
            assertEquals(0, updated.getDomainEvents().size());
        }

        @Test
        void updateForwardGeometryRejectsEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> fresh.updateForwardGeometry("  ", 10));
        }

        @Test
        void updateForwardGeometryStoresWKT() {
            String wkt = "LINESTRING(58.3 37.9, 58.4 38.0)";
            BusRoute updated = fresh.updateForwardGeometry(wkt, 500);
            assertEquals(wkt, updated.getRouteGeometryForward());
            assertEquals(500, updated.getTotalDistanceForwardMeters());
        }

        @Test
        void updateBackwardGeometryStoresWKT() {
            String wkt = "LINESTRING(58.4 38.0, 58.3 37.9)";
            BusRoute updated = fresh.updateBackwardGeometry(wkt, 500);
            assertEquals(wkt, updated.getRouteGeometryBackward());
        }

        @Test
        void updateBackwardGeometryRejectsEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> fresh.updateBackwardGeometry(null, 0));
        }

        @Test
        void clearGeometryResetsBothDirections() {
            String wkt = "LINESTRING(58.3 37.9, 58.4 38.0)";
            BusRoute withGeom = fresh
                    .updateForwardGeometry(wkt, 100)
                    .updateBackwardGeometry(wkt, 100);
            BusRoute cleared = withGeom.clearGeometry();
            assertNull(cleared.getRouteGeometryForward());
            assertNull(cleared.getRouteGeometryBackward());
            assertNull(cleared.getTotalDistanceForwardMeters());
            assertNull(cleared.getTotalDistanceBackwardMeters());
        }

        @Test
        void clearForwardGeometryOnlyTouchesForward() {
            String wkt = "LINESTRING(58.3 37.9, 58.4 38.0)";
            BusRoute withGeom = fresh
                    .updateForwardGeometry(wkt, 100)
                    .updateBackwardGeometry(wkt, 100);
            BusRoute cleared = withGeom.clearForwardGeometry();
            assertNull(cleared.getRouteGeometryForward());
            assertEquals(wkt, cleared.getRouteGeometryBackward());
        }

        @Test
        void clearBackwardGeometryOnlyTouchesBackward() {
            String wkt = "LINESTRING(58.3 37.9, 58.4 38.0)";
            BusRoute withGeom = fresh
                    .updateForwardGeometry(wkt, 100)
                    .updateBackwardGeometry(wkt, 100);
            BusRoute cleared = withGeom.clearBackwardGeometry();
            assertEquals(wkt, cleared.getRouteGeometryForward());
            assertNull(cleared.getRouteGeometryBackward());
        }

        @Test
        void getGeometryByDirectionReadsAppropriate() {
            RouteGeometry fwd = new RouteGeometry(List.of(
                    Coordinates.of(37.96, 58.32),
                    Coordinates.of(37.97, 58.33)));
            RouteGeometry bwd = new RouteGeometry(List.of(
                    Coordinates.of(37.97, 58.33),
                    Coordinates.of(37.96, 58.32)));
            BusRoute withGeom = fresh.updateRouteGeometry(fwd, bwd);
            assertNotNull(withGeom.getGeometryByDirection(RouteDirection.FORWARD));
            assertNotNull(withGeom.getGeometryByDirection(RouteDirection.BACKWARD));
        }

        @Test
        void getForwardGeometryReturnsNullOnInvalidWkt() {
            BusRoute withGarbage = fresh.toBuilder()
                    .routeGeometryForward("not valid wkt")
                    .build();
            assertNull(withGarbage.getForwardGeometry());
        }
    }

    @Nested
    class ActivationLifecycle {

        @Test
        void deactivateFlipsFlag() {
            BusRoute offline = fresh.deactivate();
            assertFalse(offline.getIsActive());
        }

        @Test
        void deactivateIsIdempotent() {
            BusRoute offline = fresh.deactivate();
            BusRoute again = offline.deactivate();
            assertSame(offline, again);
        }

        @Test
        void activateIsIdempotent() {
            BusRoute same = fresh.activate();
            assertSame(fresh, same);
        }

        @Test
        void activateFlipsBackOnline() {
            BusRoute offline = fresh.deactivate();
            BusRoute online = offline.activate();
            assertTrue(online.getIsActive());
        }
    }

    @Test
    void restoreRebuildsStateWithoutEvents() {
        BusRoute restored = BusRoute.restore(
                fresh.getId(), "29A", "Name", "tm", "en", "#FF5722",
                false, "ashgabat", 60,
                "LINESTRING(58 37, 58 38)", null,
                1500, null,
                fresh.getCreatedAt(), fresh.getUpdatedAt(), 4L);
        assertEquals(fresh.getId(), restored.getId());
        assertFalse(restored.getIsActive());
        assertEquals(4L, restored.getVersion());
        assertEquals(0, restored.getDomainEvents().size());
    }

    @Test
    void restoreDefaultsActiveAndVersionWhenNull() {
        BusRoute restored = BusRoute.restore(
                BusRouteId.generate(), "1", "Name", "", "", "#FF5722",
                null, "c", 10, null, null, null, null, null, null, null);
        assertTrue(restored.getIsActive());
        assertEquals(0L, restored.getVersion());
    }
}
