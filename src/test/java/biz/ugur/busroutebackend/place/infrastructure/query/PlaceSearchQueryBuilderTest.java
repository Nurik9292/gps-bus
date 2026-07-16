package biz.ugur.busroutebackend.place.infrastructure.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceSearchQueryBuilderTest {

    @Test
    @DisplayName("Nearby запрос содержит ST_DWithin и ST_Distance")
    void nearbyQueryContainsPostGIS() {
        var result = PlaceSearchQueryBuilder.buildNearbyQuery(37.95, 58.38, 500, null, 20);

        assertNotNull(result);
        assertTrue(result.sql().contains("ST_DWithin"));
        assertTrue(result.sql().contains("ST_Distance"));
        assertEquals(37.95, result.params().get("lat"));
        assertEquals(58.38, result.params().get("lng"));
        assertEquals(500, result.params().get("radius"));
        assertEquals(20, result.params().get("limit"));
    }

    @Test
    @DisplayName("Nearby запрос с фильтром category")
    void nearbyQueryWithCategory() {
        var result = PlaceSearchQueryBuilder.buildNearbyQuery(37.95, 58.38, 500, "MOSQUE", 20);

        assertTrue(result.sql().contains("p.category = :category"));
        assertEquals("MOSQUE", result.params().get("category"));
    }

    @Test
    @DisplayName("Nearby запрос без category")
    void nearbyQueryWithoutCategory() {
        var result = PlaceSearchQueryBuilder.buildNearbyQuery(37.95, 58.38, 500, null, 20);

        assertFalse(result.sql().contains("p.category = :category"));
    }

    @Test
    @DisplayName("Nearby запрос отсортирован по distance")
    void nearbyQueryOrderedByDistance() {
        var result = PlaceSearchQueryBuilder.buildNearbyQuery(37.95, 58.38, 500, null, 20);

        assertTrue(result.sql().contains("ORDER BY distance ASC"));
    }
}
