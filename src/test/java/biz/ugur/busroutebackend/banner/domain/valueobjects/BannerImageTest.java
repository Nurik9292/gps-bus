package biz.ugur.busroutebackend.banner.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BannerImageTest {

    private final static String IMAGE_PATH = "banners/10/08/origin_data.jpg";

    @Test
    void createBannerImageSuccessfully() {
        BannerImage bannerImage = BannerImage.of(IMAGE_PATH);
        assertNotNull(bannerImage);
        assertEquals(IMAGE_PATH, bannerImage.getValue());
    }

    @Test
    void createBannerImageFailWhenValueIsNull() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> BannerImage.of(null));
        assertEquals("Banner image URL cannot be null or empty", exception.getMessage());
    }

    @Test
    void createBannerImageFailWhenValueIsEmpty() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> BannerImage.of(""));
        assertEquals("Banner image URL cannot be null or empty", exception.getMessage());
    }
}