package biz.ugur.busroutebackend.place.infrastructure.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlaceSearchQueryBuilder Tests")
class PlaceSearchQueryBuilderTest {

    @Nested
    @DisplayName("buildSearchQuery")
    class SearchQuery {

        @Test
        @DisplayName("Короткий запрос (<=3 символа) использует ILIKE")
        void shortQueryUsesIlike() {
            var result = PlaceSearchQueryBuilder.buildSearchQuery("ТЦ", null, null, 20, 0.3);

            assertNotNull(result);
            assertTrue(result.sql().contains("ILIKE :pattern"));
            assertFalse(result.sql().contains("similarity"));
            assertEquals("%ТЦ%", result.params().get("pattern"));
            assertEquals(20, result.params().get("limit"));
        }

        @Test
        @DisplayName("Длинный запрос (>3 символа) использует similarity")
        void longQueryUsesSimilarity() {
            var result = PlaceSearchQueryBuilder.buildSearchQuery("Беркарар", null, null, 20, 0.3);

            assertNotNull(result);
            assertTrue(result.sql().contains("similarity"));
            assertTrue(result.sql().contains(":threshold"));
            assertEquals("Беркарар", result.params().get("query"));
            assertEquals(0.3, result.params().get("threshold"));
        }

        @Test
        @DisplayName("Граничный запрос (ровно 3 символа) использует ILIKE")
        void boundaryQueryUsesIlike() {
            var result = PlaceSearchQueryBuilder.buildSearchQuery("abc", null, null, 20, 0.3);

            assertTrue(result.sql().contains("ILIKE :pattern"));
            assertFalse(result.sql().contains("similarity"));
        }

        @Test
        @DisplayName("Фильтр по cityId добавляется к запросу")
        void cityIdFilterApplied() {
            var result = PlaceSearchQueryBuilder.buildSearchQuery("тест", "city-1", null, 20, 0.3);

            assertTrue(result.sql().contains("p.city_id = :cityId"));
            assertEquals("city-1", result.params().get("cityId"));
        }

        @Test
        @DisplayName("Фильтр по category добавляется к запросу")
        void categoryFilterApplied() {
            var result = PlaceSearchQueryBuilder.buildSearchQuery("тест", null, "MALL", 20, 0.3);

            assertTrue(result.sql().contains("p.category = :category"));
            assertEquals("MALL", result.params().get("category"));
        }

        @Test
        @DisplayName("Оба фильтра применяются одновременно")
        void bothFiltersApplied() {
            var result = PlaceSearchQueryBuilder.buildSearchQuery("тест", "city-1", "MALL", 20, 0.3);

            assertTrue(result.sql().contains("p.city_id = :cityId"));
            assertTrue(result.sql().contains("p.category = :category"));
        }

        @Test
        @DisplayName("Null фильтры не добавляются к запросу")
        void nullFiltersNotApplied() {
            var result = PlaceSearchQueryBuilder.buildSearchQuery("тест", null, null, 20, 0.3);

            assertFalse(result.sql().contains("p.city_id = :cityId"));
            assertFalse(result.sql().contains("p.category = :category"));
        }

        @Test
        @DisplayName("Fuzzy запрос содержит ORDER BY score DESC")
        void fuzzyQueryOrderedByScore() {
            var result = PlaceSearchQueryBuilder.buildSearchQuery("Беркарар", null, null, 20, 0.3);

            assertTrue(result.sql().contains("ORDER BY score DESC"));
        }

        @Test
        @DisplayName("Short запрос содержит ORDER BY p.name ASC")
        void shortQueryOrderedByName() {
            var result = PlaceSearchQueryBuilder.buildSearchQuery("ТЦ", null, null, 20, 0.3);

            assertTrue(result.sql().contains("ORDER BY p.name ASC"));
        }
    }

    @Nested
    @DisplayName("buildNearbyQuery")
    class NearbyQuery {

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

    @Nested
    @DisplayName("buildAutocompleteQuery")
    class AutocompleteQuery {

        @Test
        @DisplayName("Autocomplete использует ILIKE с prefix matching")
        void autocompleteUsesPrefixMatch() {
            var result = PlaceSearchQueryBuilder.buildAutocompleteQuery("Бер", null, 10);

            assertNotNull(result);
            assertTrue(result.sql().contains("ILIKE :pattern"));
            assertEquals("Бер%", result.params().get("pattern"));
            assertEquals(10, result.params().get("limit"));
        }

        @Test
        @DisplayName("Autocomplete с фильтром cityId")
        void autocompleteWithCityId() {
            var result = PlaceSearchQueryBuilder.buildAutocompleteQuery("Бер", "city-1", 10);

            assertTrue(result.sql().contains("p.city_id = :cityId"));
            assertEquals("city-1", result.params().get("cityId"));
        }

        @Test
        @DisplayName("Autocomplete возвращает DISTINCT результаты")
        void autocompleteReturnsDistinct() {
            var result = PlaceSearchQueryBuilder.buildAutocompleteQuery("Бер", null, 10);

            assertTrue(result.sql().contains("SELECT DISTINCT"));
        }
    }
}
