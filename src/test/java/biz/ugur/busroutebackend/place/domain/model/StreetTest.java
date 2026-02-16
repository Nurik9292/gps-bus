package biz.ugur.busroutebackend.place.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Street Domain Model Tests")
class StreetTest {

    private static final String NAME = "проспект Махтумкули";
    private static final String NAME_EN = "Magtymguly Avenue";
    private static final String NAME_TM = "Magtymguly şaýoly";
    private static final String CITY_ID = "city-1";

    @Test
    @DisplayName("Успешное создание улицы")
    void createStreetSuccessfully() {
        Street street = Street.create(NAME, NAME_EN, NAME_TM, CITY_ID);

        assertNotNull(street);
        assertNotNull(street.getId());
        assertEquals(NAME, street.getName());
        assertEquals(NAME_EN, street.getNameEn());
        assertEquals(NAME_TM, street.getNameTm());
        assertEquals(CITY_ID, street.getCityId());
        assertTrue(street.getIsActive());
    }

    @Test
    @DisplayName("Создание улицы с null name вызывает исключение")
    void createStreetFailsWhenNameNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                Street.create(null, NAME_EN, NAME_TM, CITY_ID));

        assertEquals("Street name cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Создание улицы с пустым name вызывает исключение")
    void createStreetFailsWhenNameEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                Street.create("  ", NAME_EN, NAME_TM, CITY_ID));

        assertEquals("Street name cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Обновление улицы")
    void updateStreetSuccessfully() {
        Street street = Street.create(NAME, NAME_EN, NAME_TM, CITY_ID);
        String newName = "улица Атамурата Ниязова";

        Street updated = street.updateInfo(newName, null, null, null);

        assertEquals(newName, updated.getName());
        assertEquals(NAME_EN, updated.getNameEn());
        assertEquals(NAME_TM, updated.getNameTm());
        assertEquals(CITY_ID, updated.getCityId());
    }

    @Test
    @DisplayName("Обновление улицы с null значениями сохраняет существующие")
    void updateStreetWithNullsPreservesExisting() {
        Street street = Street.create(NAME, NAME_EN, NAME_TM, CITY_ID);

        Street updated = street.updateInfo(null, null, null, null);

        assertEquals(NAME, updated.getName());
        assertEquals(NAME_EN, updated.getNameEn());
        assertEquals(NAME_TM, updated.getNameTm());
        assertEquals(CITY_ID, updated.getCityId());
    }

    @Test
    @DisplayName("Создание улицы обрезает пробелы")
    void createStreetTrimsStrings() {
        Street street = Street.create("  " + NAME + "  ", "  " + NAME_EN + "  ", null, CITY_ID);

        assertEquals(NAME, street.getName());
        assertEquals(NAME_EN, street.getNameEn());
    }

    @Test
    @DisplayName("Создание улицы с минимальными параметрами")
    void createStreetWithMinimalParams() {
        Street street = Street.create(NAME, null, null, null);

        assertNotNull(street);
        assertEquals(NAME, street.getName());
        assertNull(street.getNameEn());
        assertNull(street.getCityId());
        assertTrue(street.getIsActive());
    }
}
