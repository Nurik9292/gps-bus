package biz.ugur.busroutebackend.banner.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BannerIdTest {

    private final static String BANNER_ID = "1";

    @Test
    void generateBannerIdSuccessfully() {
        BannerId bannerId = BannerId.generate();
        assertNotNull(bannerId);
    }

    @Test
    void  createBannerIdSuccessfully() {
        BannerId bannerId = BannerId.of(BANNER_ID);

        assertNotNull(bannerId);
        assertEquals(BANNER_ID, bannerId.getValue());
    }

    @Test
    void createBannerIdFailWhenValueIsNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> BannerId.of(null));
        assertEquals("Banner ID cannot be null or empty", exception.getMessage());
    }

    @Test
    void createBannerIdFailWhenValueIsEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> BannerId.of(""));
        assertEquals("Banner ID cannot be null or empty", exception.getMessage());
    }
}