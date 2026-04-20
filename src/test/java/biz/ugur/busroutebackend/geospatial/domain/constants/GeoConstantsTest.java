package biz.ugur.busroutebackend.geospatial.domain.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoConstantsTest {

    @Test
    void getDocumentationIncludesKeyFacts() {
        String doc = GeoConstants.getDocumentation();
        assertNotNull(doc);
        assertTrue(doc.contains("Earth Radius"));
        assertTrue(doc.contains("WGS84"));
        assertTrue(doc.contains("Haversine"));
    }

    @Test
    void walkingSpeedsConsistent() {
        double kmh = GeoConstants.AVERAGE_WALKING_SPEED_KMH;
        double mPerMin = GeoConstants.AVERAGE_WALKING_SPEED_M_PER_MIN;
        double mPerSec = GeoConstants.AVERAGE_WALKING_SPEED_M_PER_SEC;
        assertEquals(kmh * 1000.0 / 60.0, mPerMin, 1e-9);
        assertEquals(kmh * 1000.0 / 3600.0, mPerSec, 1e-9);
    }

    @Test
    void layeredSearchRadiusAndMaxStopsHaveSameLength() {
        assertEquals(GeoConstants.LAYERED_SEARCH_RADIUSES_KM.length,
                GeoConstants.MAX_STOPS_PER_LAYER.length);
        for (double r : GeoConstants.LAYERED_SEARCH_RADIUSES_KM) {
            assertTrue(r > 0);
        }
    }

    @Test
    void searchRadiusConstantsOrdered() {
        assertTrue(GeoConstants.MIN_SEARCH_RADIUS_METERS < GeoConstants.DEFAULT_SEARCH_RADIUS_METERS);
        assertTrue(GeoConstants.DEFAULT_SEARCH_RADIUS_METERS < GeoConstants.MAX_SEARCH_RADIUS_METERS);
    }

    @Test
    void walkingTimeConstantsOrdered() {
        assertTrue(GeoConstants.MIN_WALKING_TIME_MINUTES < GeoConstants.REASONABLE_WALKING_TIME_MINUTES);
        assertTrue(GeoConstants.REASONABLE_WALKING_TIME_MINUTES <= GeoConstants.MAX_WALKING_TIME_MINUTES);
    }

    @Test
    void trafficMultipliersOrdered() {
        assertTrue(GeoConstants.TRAFFIC_MULTIPLIER_NO_TRAFFIC < GeoConstants.TRAFFIC_MULTIPLIER_LIGHT);
        assertTrue(GeoConstants.TRAFFIC_MULTIPLIER_LIGHT < GeoConstants.TRAFFIC_MULTIPLIER_MODERATE);
        assertTrue(GeoConstants.TRAFFIC_MULTIPLIER_MODERATE < GeoConstants.TRAFFIC_MULTIPLIER_HEAVY);
    }

    @Test
    void urbanCorrectionHasSaneRange() {
        assertTrue(GeoConstants.MIN_URBAN_CORRECTION_MINUTES < GeoConstants.MAX_URBAN_CORRECTION_MINUTES);
    }

    @Test
    void unitConversionConstantsConsistent() {
        assertEquals(1000.0, GeoConstants.METERS_PER_KILOMETER);
        assertEquals(1609.34, GeoConstants.METERS_PER_MILE);
        assertEquals(GeoConstants.METERS_PER_MILE / GeoConstants.METERS_PER_KILOMETER,
                GeoConstants.KILOMETERS_PER_MILE, 1e-9);
    }

    @Test
    void polygonAndLineStringLimits() {
        assertEquals(2, GeoConstants.MIN_LINESTRING_POINTS);
        assertEquals(4, GeoConstants.MIN_POLYGON_POINTS);
        assertTrue(GeoConstants.MAX_GEOMETRY_POINTS > GeoConstants.MIN_POLYGON_POINTS);
    }

    @Test
    void earthRadiusIsRealistic() {
        assertEquals(6_371_000.0, GeoConstants.EARTH_RADIUS_METERS);
    }

    @Test
    void sridIsWgs84() {
        assertEquals(4326, GeoConstants.SRID_WGS84);
    }
}
