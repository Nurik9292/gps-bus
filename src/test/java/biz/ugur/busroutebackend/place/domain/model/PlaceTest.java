package biz.ugur.busroutebackend.place.domain.model;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Place Domain Model Tests")
class PlaceTest {

    private static final String NAME = "Беркарар ТЦ";
    private static final String NAME_EN = "Berkarar Mall";
    private static final String NAME_TM = "Berkarar söwda merkezi";
    private static final String DESCRIPTION = "Крупный торговый центр";
    private static final String ADDRESS = "пр. Битарап Туркменистан, 78";
    private static final String CITY_ID = "city-1";
    private static final BigDecimal LATITUDE = new BigDecimal("37.9500");
    private static final BigDecimal LONGITUDE = new BigDecimal("58.3800");

    @Test
    @DisplayName("Успешное создание места")
    void createPlaceSuccessfully() {
        Place place = Place.create(NAME, NAME_EN, NAME_TM, DESCRIPTION, ADDRESS,
                PlaceCategory.MALL, CITY_ID, LATITUDE, LONGITUDE, null);

        assertNotNull(place);
        assertNotNull(place.getId());
        assertEquals(NAME, place.getName());
        assertEquals(NAME_EN, place.getNameEn());
        assertEquals(NAME_TM, place.getNameTm());
        assertEquals(DESCRIPTION, place.getDescription());
        assertEquals(ADDRESS, place.getAddress());
        assertEquals(PlaceCategory.MALL, place.getCategory());
        assertEquals(CITY_ID, place.getCityId());
        assertEquals(LATITUDE, place.getLatitude());
        assertEquals(LONGITUDE, place.getLongitude());
        assertTrue(place.getIsActive());
    }

    @Test
    @DisplayName("Создание места с null name вызывает исключение")
    void createPlaceFailsWhenNameNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                Place.create(null, NAME_EN, NAME_TM, DESCRIPTION, ADDRESS,
                        PlaceCategory.MALL, CITY_ID, LATITUDE, LONGITUDE, null));

        assertEquals("Place name cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Создание места с пустым name вызывает исключение")
    void createPlaceFailsWhenNameEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                Place.create("  ", NAME_EN, NAME_TM, DESCRIPTION, ADDRESS,
                        PlaceCategory.MALL, CITY_ID, LATITUDE, LONGITUDE, null));

        assertEquals("Place name cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Создание места с null category вызывает исключение")
    void createPlaceFailsWhenCategoryNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                Place.create(NAME, NAME_EN, NAME_TM, DESCRIPTION, ADDRESS,
                        null, CITY_ID, LATITUDE, LONGITUDE, null));

        assertEquals("Place category cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Создание места с null координатами вызывает исключение")
    void createPlaceFailsWhenCoordinatesNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                Place.create(NAME, NAME_EN, NAME_TM, DESCRIPTION, ADDRESS,
                        PlaceCategory.MALL, CITY_ID, null, LONGITUDE, null));

        assertEquals("Coordinates cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Обновление места")
    void updatePlaceSuccessfully() {
        Place place = Place.create(NAME, NAME_EN, NAME_TM, DESCRIPTION, ADDRESS,
                PlaceCategory.MALL, CITY_ID, LATITUDE, LONGITUDE, null);

        String newName = "Ашхабад Сити";
        BigDecimal newLat = new BigDecimal("37.9600");

        Place updated = place.updateInfo(newName, null, null, null, null,
                PlaceCategory.LANDMARK, null, newLat, null);

        assertEquals(newName, updated.getName());
        assertEquals(NAME_EN, updated.getNameEn());
        assertEquals(PlaceCategory.LANDMARK, updated.getCategory());
        assertEquals(newLat, updated.getLatitude());
        assertEquals(LONGITUDE, updated.getLongitude());
    }

    @Test
    @DisplayName("Обновление места с null значениями не перезаписывает существующие")
    void updatePlaceWithNullsPreservesExisting() {
        Place place = Place.create(NAME, NAME_EN, NAME_TM, DESCRIPTION, ADDRESS,
                PlaceCategory.MALL, CITY_ID, LATITUDE, LONGITUDE, null);

        Place updated = place.updateInfo(null, null, null, null, null,
                null, null, null, null);

        assertEquals(NAME, updated.getName());
        assertEquals(NAME_EN, updated.getNameEn());
        assertEquals(NAME_TM, updated.getNameTm());
        assertEquals(PlaceCategory.MALL, updated.getCategory());
        assertEquals(LATITUDE, updated.getLatitude());
    }

    @Test
    @DisplayName("Создание места обрезает пробелы в названиях")
    void createPlaceTrimsStrings() {
        Place place = Place.create("  " + NAME + "  ", "  " + NAME_EN + "  ", "  " + NAME_TM + "  ",
                DESCRIPTION, ADDRESS, PlaceCategory.MALL, CITY_ID, LATITUDE, LONGITUDE, null);

        assertEquals(NAME, place.getName());
        assertEquals(NAME_EN, place.getNameEn());
        assertEquals(NAME_TM, place.getNameTm());
    }

    @Test
    @DisplayName("toCoordinates возвращает корректные координаты")
    void toCoordinatesReturnsCorrectValues() {
        Place place = Place.create(NAME, NAME_EN, NAME_TM, DESCRIPTION, ADDRESS,
                PlaceCategory.MALL, CITY_ID, LATITUDE, LONGITUDE, null);

        Coordinates coords = place.toCoordinates();

        assertNotNull(coords);
        assertEquals(0, LATITUDE.compareTo(coords.getLatitude()));
        assertEquals(0, LONGITUDE.compareTo(coords.getLongitude()));
    }

    @Test
    @DisplayName("Создание места с минимальными параметрами")
    void createPlaceWithMinimalParams() {
        Place place = Place.create(NAME, null, null, null, null,
                PlaceCategory.OTHER, null, LATITUDE, LONGITUDE, null);

        assertNotNull(place);
        assertEquals(NAME, place.getName());
        assertNull(place.getNameEn());
        assertNull(place.getNameTm());
        assertNull(place.getDescription());
        assertNull(place.getCityId());
        assertTrue(place.getIsActive());
    }
}
