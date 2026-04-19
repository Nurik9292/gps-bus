package biz.ugur.busroutebackend.banner.domain.enums;

import biz.ugur.busroutebackend.banner.domain.exceptions.BannerValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BannerTypeTest {

    @Test
    void fromValueIsCaseInsensitive() {
        assertEquals(BannerType.MAIN, BannerType.fromValue("main"));
        assertEquals(BannerType.MAIN, BannerType.fromValue("MAIN"));
        assertEquals(BannerType.STOP_BUTTON, BannerType.fromValue("stop-button"));
        assertEquals(BannerType.STOP_BUTTON, BannerType.fromValue("Stop-Button"));
    }

    @Test
    void fromValueRejectsUnknownValue() {
        assertThrows(BannerValidationException.class, () -> BannerType.fromValue("unknown"));
    }

    @Test
    void valuesStoreExpectedString() {
        assertEquals("main", BannerType.MAIN.getValue());
        assertEquals("stops", BannerType.STOPS.getValue());
        assertEquals("routes", BannerType.ROUTES.getValue());
        assertEquals("places", BannerType.PLACES.getValue());
        assertEquals("popup", BannerType.POPUP.getValue());
        assertEquals("stop-button", BannerType.STOP_BUTTON.getValue());
    }

    @Test
    void toStringDoesNotExplode() {
        for (BannerType t : BannerType.values()) {
            assertNotNull(t.toString());
        }
    }
}
