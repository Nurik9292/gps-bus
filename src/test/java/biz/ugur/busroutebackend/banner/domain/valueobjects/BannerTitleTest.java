package biz.ugur.busroutebackend.banner.domain.valueobjects;

import biz.ugur.busroutebackend.banner.domain.exceptions.BannerValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BannerTitleTest {

    private final static String TITLE = "title";

    @Test
    void createBannerTitleSuccessfully() {
        BannerTitle bannerTitle = BannerTitle.of(TITLE);
        assertNotNull(bannerTitle);
        assertEquals(TITLE, bannerTitle.getValue());
    }

    @Test
    void createBannerTitleFailWhenValueIsNull() {
        Exception exception = assertThrows(BannerValidationException.class, () -> BannerTitle.of(null));
        assertEquals("Banner title cannot be null or empty", exception.getMessage());
    }

    @Test
    void createBannerTitleSuccessfullyWithValue() {
        Exception exception = assertThrows(BannerValidationException.class, () -> BannerTitle.of(""));
        assertEquals("Banner title cannot be null or empty", exception.getMessage());
    }
}