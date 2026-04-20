package biz.ugur.busroutebackend.transport.infrastructure.redis;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpsPointTest {

    @Test
    void constructorAssignsFields() {
        LocalDateTime time = LocalDateTime.of(2026, 4, 20, 12, 0);
        GpsPoint point = new GpsPoint(37.96, 58.33, 45.0, time);

        assertEquals(37.96, point.getLat());
        assertEquals(58.33, point.getLon());
        assertEquals(45.0, point.getSpeed());
        assertEquals(time, point.getTime());
    }

    @Test
    void ofFactoryProvidesDefaultsForNulls() {
        GpsPoint point = GpsPoint.of(null, null, null, null);

        assertEquals(0.0, point.getLat());
        assertEquals(0.0, point.getLon());
        assertEquals(0.0, point.getSpeed());
        assertNotNull(point.getTime());
    }

    @Test
    void ofFactoryPassesThroughNonNullValues() {
        LocalDateTime time = LocalDateTime.of(2026, 4, 20, 12, 0);
        GpsPoint point = GpsPoint.of(37.96, 58.33, 50.0, time);

        assertEquals(37.96, point.getLat());
        assertEquals(time, point.getTime());
    }

    @Test
    void gpsHistoryPointAccessorsMatchFields() {
        GpsPoint point = new GpsPoint(37.96, 58.33, 20.0, LocalDateTime.now());

        assertEquals(point.getLat(), point.getLatitude());
        assertEquals(point.getLon(), point.getLongitude());
        assertEquals(point.getTime(), point.getTimestamp());
    }

    @Test
    void toStringIncludesCoordinates() {
        GpsPoint point = new GpsPoint(37.96, 58.33, 20.0, LocalDateTime.now());
        String text = point.toString();

        assertTrue(text.contains("37"));
        assertTrue(text.contains("58"));
    }
}
