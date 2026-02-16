package biz.ugur.busroutebackend.place.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlaceCategory Enum Tests")
class PlaceCategoryTest {

    @Test
    @DisplayName("Все категории доступны")
    void allCategoriesExist() {
        PlaceCategory[] categories = PlaceCategory.values();
        assertEquals(14, categories.length);
    }

    @Test
    @DisplayName("valueOf работает для всех категорий")
    void valueOfWorksForAll() {
        assertDoesNotThrow(() -> PlaceCategory.valueOf("MALL"));
        assertDoesNotThrow(() -> PlaceCategory.valueOf("BAZAAR"));
        assertDoesNotThrow(() -> PlaceCategory.valueOf("RESTAURANT"));
        assertDoesNotThrow(() -> PlaceCategory.valueOf("MOSQUE"));
        assertDoesNotThrow(() -> PlaceCategory.valueOf("PARK"));
        assertDoesNotThrow(() -> PlaceCategory.valueOf("LANDMARK"));
        assertDoesNotThrow(() -> PlaceCategory.valueOf("HOTEL"));
        assertDoesNotThrow(() -> PlaceCategory.valueOf("HOSPITAL"));
        assertDoesNotThrow(() -> PlaceCategory.valueOf("EDUCATION"));
        assertDoesNotThrow(() -> PlaceCategory.valueOf("GOVERNMENT"));
        assertDoesNotThrow(() -> PlaceCategory.valueOf("BANK"));
        assertDoesNotThrow(() -> PlaceCategory.valueOf("PHARMACY"));
        assertDoesNotThrow(() -> PlaceCategory.valueOf("GAS_STATION"));
        assertDoesNotThrow(() -> PlaceCategory.valueOf("OTHER"));
    }

    @Test
    @DisplayName("Невалидная категория вызывает исключение")
    void invalidCategoryThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                PlaceCategory.valueOf("INVALID"));
    }
}
