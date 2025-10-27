package biz.ugur.busroutebackend.admin.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CityTest {

    private City city;
    private static final String CITY_NAME = "Ashgabat";
    private static final String CITY_NAME_TM = "Aşgabat";
    private static final Integer DISPLAY_ORDER = 1;

    @BeforeEach
    void setUp() {
        city = City.create(CITY_NAME, CITY_NAME_TM, DISPLAY_ORDER);
    }

    @Test
    void create_ShouldCreateNewCityWithCorrectProperties() {

        assertNotNull(city);
        assertNotNull(city.getId());
        assertEquals(CITY_NAME, city.getName());
        assertEquals(CITY_NAME_TM, city.getNameTm());
        assertEquals(DISPLAY_ORDER, city.getDisplayOrder());
        assertTrue(city.getIsActive());
    }

    @Test
    void create_ShouldUseDefaultDisplayOrder_WhenNull() {

        City cityWithDefaultOrder = City.create("Balkanabat", "Balkanabat", null);


        assertEquals(0, cityWithDefaultOrder.getDisplayOrder());
    }

    @Test
    void create_ShouldThrowException_WhenNameIsNull() {

        assertThrows(IllegalArgumentException.class, () ->
                City.create(null, CITY_NAME_TM, DISPLAY_ORDER)
        );
    }

    @Test
    void create_ShouldThrowException_WhenNameIsEmpty() {

        assertThrows(IllegalArgumentException.class, () ->
                City.create("   ", CITY_NAME_TM, DISPLAY_ORDER)
        );
    }

    @Test
    void updateCity_ShouldReturnNewInstanceWithUpdatedProperties() {

        String newName = "Mary";
        String newNameTm = "Mary";
        Integer newDisplayOrder = 2;


        City updatedCity = city.updateCity(newName, newNameTm, newDisplayOrder);


        assertNotNull(updatedCity);
        assertNotSame(city, updatedCity);
        assertEquals(CITY_NAME, city.getName());
        assertEquals(CITY_NAME_TM, city.getNameTm());
        assertEquals(DISPLAY_ORDER, city.getDisplayOrder());

        assertEquals(newName, updatedCity.getName());
        assertEquals(newNameTm, updatedCity.getNameTm());
        assertEquals(newDisplayOrder, updatedCity.getDisplayOrder());
    }

    @Test
    void updateCity_ShouldReturnSameInstance_WhenNoChanges() {

        City unchanged = city.updateCity(null, null, null);


        assertSame(city, unchanged);
    }

    @Test
    void updateCity_ShouldTrimWhitespace() {

        String nameWithSpaces = "  Turkmenabat  ";
        String nameTmWithSpaces = "  Türkmenabat  ";


        City updatedCity = city.updateCity(nameWithSpaces, nameTmWithSpaces, 3);


        assertEquals("Turkmenabat", updatedCity.getName());
        assertEquals("Türkmenabat", updatedCity.getNameTm());
    }

    @Test
    void updateCity_ShouldUpdateOnlyProvidedFields() {

        City updatedName = city.updateCity("NewName", null, null);


        assertEquals("NewName", updatedName.getName());
        assertEquals(CITY_NAME_TM, updatedName.getNameTm());
        assertEquals(DISPLAY_ORDER, updatedName.getDisplayOrder());
    }

    @Test
    void deactivate_ShouldReturnNewInstanceWithIsActiveFalse() {

        City deactivatedCity = city.deactivate();


        assertNotNull(deactivatedCity);
        assertNotSame(city, deactivatedCity);
        assertTrue(city.getIsActive());
        assertFalse(deactivatedCity.getIsActive());
    }

    @Test
    void deactivate_ShouldReturnSameInstance_WhenAlreadyInactive() {

        City inactiveCity = city.deactivate();


        City stillInactive = inactiveCity.deactivate();


        assertSame(inactiveCity, stillInactive);
    }

    @Test
    void activate_ShouldReturnNewInstanceWithIsActiveTrue() {

        City inactiveCity = city.deactivate();


        City activatedCity = inactiveCity.activate();


        assertNotNull(activatedCity);
        assertNotSame(inactiveCity, activatedCity);
        assertFalse(inactiveCity.getIsActive());
        assertTrue(activatedCity.getIsActive());
    }

    @Test
    void activate_ShouldReturnSameInstance_WhenAlreadyActive() {

        City stillActive = city.activate();


        assertSame(city, stillActive);
    }

    @Test
    void immutabilityTest_OriginalShouldRemainUnchanged() {

        City original = City.create("Original", "Original TM", 1);
        var originalName = original.getName();
        var originalNameTm = original.getNameTm();
        var originalDisplayOrder = original.getDisplayOrder();
        var originalIsActive = original.getIsActive();


        City afterUpdate = original.updateCity("Updated", "Updated TM", 2);
        City afterDeactivate = original.deactivate();


        assertEquals(originalName, original.getName());
        assertEquals(originalNameTm, original.getNameTm());
        assertEquals(originalDisplayOrder, original.getDisplayOrder());
        assertEquals(originalIsActive, original.getIsActive());


        assertNotSame(original, afterUpdate);
        assertNotSame(original, afterDeactivate);
    }

    @Test
    void chainingOperations_ShouldWork() {

        City result = city
                .updateCity("Balkanabat", "Balkanabat", 5)
                .deactivate();


        assertEquals("Balkanabat", result.getName());
        assertEquals("Balkanabat", result.getNameTm());
        assertEquals(5, result.getDisplayOrder());
        assertFalse(result.getIsActive());


        assertEquals(CITY_NAME, city.getName());
        assertEquals(CITY_NAME_TM, city.getNameTm());
        assertEquals(DISPLAY_ORDER, city.getDisplayOrder());
        assertTrue(city.getIsActive());
    }

    @Test
    void multipleUpdates_ShouldCreateMultipleInstances() {

        City updated1 = city.updateCity("City1", "City1 TM", 1);
        City updated2 = updated1.updateCity("City2", "City2 TM", 2);
        City updated3 = updated2.updateCity("City3", "City3 TM", 3);


        assertNotSame(city, updated1);
        assertNotSame(updated1, updated2);
        assertNotSame(updated2, updated3);
        assertNotSame(city, updated3);


        assertEquals(CITY_NAME, city.getName());
        assertEquals("City1", updated1.getName());
        assertEquals("City2", updated2.getName());
        assertEquals("City3", updated3.getName());
    }

    @Test
    void activateDeactivateCycle_ShouldWork() {

        City deactivated = city.deactivate();
        City reactivated = deactivated.activate();
        City deactivatedAgain = reactivated.deactivate();


        assertTrue(city.getIsActive());
        assertFalse(deactivated.getIsActive());
        assertTrue(reactivated.getIsActive());
        assertFalse(deactivatedAgain.getIsActive());


        assertNotSame(city, deactivated);
        assertNotSame(deactivated, reactivated);
        assertNotSame(reactivated, deactivatedAgain);
    }
}
