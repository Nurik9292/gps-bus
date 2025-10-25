package biz.ugur.busroutebackend.banner.domain.model;

import biz.ugur.busroutebackend.banner.domain.events.BannerCreatedEvent;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerImage;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerTitle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
class BannerTest {

    private final static String TARGET_URL = "http:/localhost:8080/";
    private final static String TITLE = "Banner Title";
    private final static String IMAGE_PATH = "banners/10/08/origin_data.jpg";
    private final static String CONTENT = "Banner Content";
    private final static int DISPLAY_ORDER = 1;
    private final static long VERSION = 1L;
    private final static String BANNER_ID = "1";
    private final static boolean IS_ACTIVE = true;
    private final static Instant CREATED_AT = Instant.now();
    private final static Instant UPDATED_AT = Instant.now();


    private final static String UPDATE_TARGET_URL = "http:/test:8080/";
    private final static String UPDATE_TITLE = "Banner New Title";
    private final static String UPDATE_IMAGE_PATH = "banners/10/08/origin_new.jpg";
    private final static String UPDATE_CONTENT = "Banner NEW Content";
    private final static int UPDATE_DISPLAY_ORDER = 2;
    private final static long UPDATE_VERSION = 2L;
    private final static Instant UPDATE_UPDATED_AT = Instant.now().minusSeconds(10);

    private BannerTitle createTitle() {
        return BannerTitle.of(TITLE);
    }

    private BannerImage createImage() {
        return BannerImage.of(IMAGE_PATH);
    }

    private BannerPeriod createPeriod() {
        return BannerPeriod.between(LocalDateTime.now(), LocalDateTime.now().plusDays(1));
    }


    @Test
    void createBannerSuccessfully() {
        BannerTitle title = createTitle();
        BannerType type = BannerType.MAIN;
        BannerPeriod period = createPeriod();
        BannerImage image = createImage();

        Banner banner = Banner.create(title, type, period, image, TARGET_URL, DISPLAY_ORDER, CONTENT);
        banner.setCreatedAt(CREATED_AT);
        banner.setUpdatedAt(UPDATED_AT);
        banner.setVersion(VERSION);

        assertNotNull(banner);
        assertNotNull(banner.getId());
        assertEquals(title, banner.getTitle());
        assertEquals(TITLE, banner.getTitle().getValue());
        assertEquals(CONTENT, banner.getContent());
        assertEquals(type, banner.getType());
        assertEquals(period, banner.getPeriod());
        assertTrue(period.getStartTime().isEqual(banner.getPeriod().getStartTime()));
        assertTrue(period.getEndTime().isEqual(banner.getPeriod().getEndTime()));
        assertEquals(TARGET_URL, banner.getTargetUrl());
        assertTrue(banner.getIsActive());
        assertEquals(DISPLAY_ORDER, banner.getDisplayOrder());
        assertNotNull(banner.getCreatedAt());
        assertNotNull(banner.getUpdatedAt());
        assertEquals(VERSION, banner.getVersion());

        assertEquals(1, banner.getDomainEvents().size());
        assertInstanceOf(BannerCreatedEvent.class, banner.getDomainEvents().getFirst());
    }


    @Test
    void createBannerFailsWhenTitleInvalid() {
        BannerType type = BannerType.MAIN;
        BannerPeriod period = createPeriod();
        BannerImage image = createImage();

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                Banner.create(BannerTitle.of(""), type, period, image, TARGET_URL, DISPLAY_ORDER, CONTENT));

        assertEquals("Banner title cannot be null or empty", exception.getMessage());
    }


    @Test
    void createBannerFailsWhenPeriodInvalid() {
        BannerType type = BannerType.MAIN;
        BannerImage image = createImage();
        BannerTitle title = createTitle();

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                Banner.create(title, type, BannerPeriod.between(LocalDateTime.now().plusDays(1), LocalDateTime.now()),
                image, TARGET_URL, DISPLAY_ORDER, CONTENT
        ));

        assertEquals("Banner Period endTime cannot be before startTime", exception.getMessage());
    }

    @Test
    void createBannerFailsWhenImageInvalid() {
        BannerType type = BannerType.MAIN;
        BannerTitle title = createTitle();
        BannerPeriod period = createPeriod();

        Exception exception = assertThrows(IllegalArgumentException.class, () ->   Banner.create(
                title,
                type,
                period,
                BannerImage.of(""),
                TARGET_URL,
                DISPLAY_ORDER,
                CONTENT
        ));

        assertEquals("Banner image URL cannot be null or empty", exception.getMessage());
    }


    @Test
    void restoreBannerSuccessfully() {
        BannerId id = BannerId.of(BANNER_ID);
        BannerTitle title = createTitle();
        BannerType type = BannerType.MAIN;
        BannerPeriod period = createPeriod();
        BannerImage image = createImage();
        Banner banner = Banner.restore(id, title, type, period, image,
                TARGET_URL, IS_ACTIVE, DISPLAY_ORDER, CONTENT, CREATED_AT, UPDATED_AT, VERSION);

        assertNotNull(banner);
        assertNotNull(banner.getId());
        assertEquals(TITLE, banner.getTitle().getValue());
        assertEquals(BANNER_ID, banner.getId().getValue());
        assertEquals(type, banner.getType());
        assertTrue(period.getStartTime().isEqual(banner.getPeriod().getStartTime()));
        assertTrue(period.getEndTime().isEqual(banner.getPeriod().getEndTime()));
        assertEquals(TARGET_URL, banner.getTargetUrl());
        assertTrue(banner.getIsActive());
        assertEquals(DISPLAY_ORDER, banner.getDisplayOrder());
        assertNotNull(banner.getCreatedAt());
        assertNotNull(banner.getUpdatedAt());
        assertEquals(VERSION, banner.getVersion());
        assertEquals(CONTENT, banner.getContent());
        assertEquals(IMAGE_PATH, banner.getImageUrl().getValue());
    }

    @Test
    void updateBannerSuccessfully() {
        BannerTitle title = createTitle();
        BannerType type = BannerType.MAIN;
        BannerPeriod period = createPeriod();
        BannerImage image = createImage();

        Banner banner = Banner.create(title, type, period, image, TARGET_URL, DISPLAY_ORDER, CONTENT);
        banner.setCreatedAt(CREATED_AT);
        banner.setUpdatedAt(UPDATED_AT);
        banner.setVersion(VERSION);


        BannerTitle newTitle = BannerTitle.of(UPDATE_TITLE);
        BannerType newType = BannerType.ROUTES;
        BannerPeriod newPeriod = BannerPeriod.between(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));
        BannerImage newImage = BannerImage.of(UPDATE_IMAGE_PATH);

        banner.updateBanner(newTitle, newType, newPeriod, newImage,
                UPDATE_TARGET_URL, UPDATE_DISPLAY_ORDER, UPDATE_CONTENT);

        banner.setVersion(UPDATE_VERSION);
        banner.setUpdatedAt(UPDATE_UPDATED_AT);


        assertNotNull(banner.getId());
        assertEquals(UPDATE_TITLE, banner.getTitle().getValue());
        assertEquals(newType, banner.getType());
        assertTrue(newPeriod.getStartTime().isEqual(banner.getPeriod().getStartTime()));
        assertTrue(newPeriod.getEndTime().isEqual(banner.getPeriod().getEndTime()));
        assertEquals(UPDATE_IMAGE_PATH, banner.getImageUrl().getValue());
        assertEquals(UPDATE_DISPLAY_ORDER, banner.getDisplayOrder());
        assertEquals(UPDATE_CONTENT, banner.getContent());
        assertEquals(UPDATE_TARGET_URL, banner.getTargetUrl());
        assertEquals(UPDATE_VERSION, banner.getVersion());
        assertEquals(UPDATE_UPDATED_AT, banner.getUpdatedAt());

    }

    @Test
    void updateBannerWithNullValuesDoesNotOverwrite() {
        BannerTitle title = createTitle();
        BannerType type = BannerType.MAIN;
        BannerPeriod period = createPeriod();
        BannerImage image = createImage();

        Banner banner = Banner.create(title, type, period, image, TARGET_URL, DISPLAY_ORDER, CONTENT);
        banner.setCreatedAt(CREATED_AT);
        banner.setUpdatedAt(UPDATED_AT);
        banner.setVersion(VERSION);

        BannerTitle newTitle = BannerTitle.of(UPDATE_TITLE);
        BannerType newType = BannerType.ROUTES;
        BannerPeriod newPeriod = BannerPeriod.between(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));
        BannerImage newImage = BannerImage.of(UPDATE_IMAGE_PATH);

        banner.updateBanner(newTitle, newType, newPeriod, newImage,null, null, null);

        banner.setVersion(UPDATE_VERSION);
        banner.setUpdatedAt(UPDATE_UPDATED_AT);

        assertNotNull(banner.getId());
        assertEquals(UPDATE_TITLE, banner.getTitle().getValue());
        assertEquals(newType, banner.getType());
        assertTrue(newPeriod.getStartTime().isEqual(banner.getPeriod().getStartTime()));
        assertTrue(newPeriod.getEndTime().isEqual(banner.getPeriod().getEndTime()));
        assertEquals(UPDATE_IMAGE_PATH, banner.getImageUrl().getValue());
        assertEquals(DISPLAY_ORDER, banner.getDisplayOrder());
        assertEquals(CONTENT, banner.getContent());
        assertEquals(TARGET_URL, banner.getTargetUrl());
        assertEquals(UPDATE_VERSION, banner.getVersion());
        assertEquals(UPDATE_UPDATED_AT, banner.getUpdatedAt());
    }


    @Test
    void updateBannerFailsWhenTitleInvalid() {
        BannerType type = BannerType.MAIN;
        BannerImage image = createImage();
        BannerTitle title = createTitle();
        BannerPeriod period = createPeriod();

        Banner banner = Banner.create(title, type, period, image, TARGET_URL, DISPLAY_ORDER, CONTENT);

        Exception exception = assertThrows(IllegalArgumentException.class, () ->   banner.updateBanner(
                BannerTitle.of(" "),
                BannerType.POPUP,
                BannerPeriod.between(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2)),
                BannerImage.of(UPDATE_IMAGE_PATH),
                UPDATE_TARGET_URL,
                UPDATE_DISPLAY_ORDER,
                UPDATE_CONTENT
        ));

        assertEquals("Banner title cannot be null or empty", exception.getMessage());
    }


    @Test
    void updateBannerFailsWhenPeriodInvalid() {
        BannerType type = BannerType.MAIN;
        BannerImage image = createImage();
        BannerTitle title = createTitle();
        BannerPeriod period = createPeriod();

        Banner banner = Banner.create(title, type, period, image, TARGET_URL, DISPLAY_ORDER, CONTENT);

        Exception exception = assertThrows(IllegalArgumentException.class, () ->   banner.updateBanner(
                BannerTitle.of(UPDATE_TITLE),
                BannerType.POPUP,
                BannerPeriod.between(LocalDateTime.now().plusDays(1), LocalDateTime.now()),
                BannerImage.of(UPDATE_IMAGE_PATH),
                UPDATE_TARGET_URL,
                UPDATE_DISPLAY_ORDER,
                UPDATE_CONTENT
                ));

        assertEquals("Banner Period endTime cannot be before startTime", exception.getMessage());
    }

    @Test
    void updateBannerFailsWhenImageInvalid() {
        BannerType type = BannerType.MAIN;
        BannerImage image = createImage();
        BannerTitle title = createTitle();
        BannerPeriod period = createPeriod();

        Banner banner = Banner.create(title, type, period, image, TARGET_URL, DISPLAY_ORDER, CONTENT);

        Exception exception = assertThrows(IllegalArgumentException.class, () ->   banner.updateBanner(
                BannerTitle.of(UPDATE_TITLE),
                BannerType.POPUP,
                BannerPeriod.between(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2)),
                BannerImage.of(" "),
                UPDATE_TARGET_URL,
                UPDATE_DISPLAY_ORDER,
                UPDATE_CONTENT
        ));

        assertEquals("Banner image URL cannot be null or empty", exception.getMessage());
    }

    @Test
    void deactivateBannerSuccess() {
        BannerType type = BannerType.MAIN;
        BannerImage image = createImage();
        BannerTitle title = createTitle();
        BannerPeriod period = createPeriod();

        Banner banner = Banner.create(title, type, period, image, TARGET_URL, DISPLAY_ORDER, CONTENT);

        banner.deactivate();

        assertFalse(banner.getIsActive());
    }

    @Test
    void activateBannerSuccess() {
        BannerType type = BannerType.MAIN;
        BannerImage image = createImage();
        BannerTitle title = createTitle();
        BannerPeriod period = createPeriod();

        Banner banner = Banner.create(title, type, period, image, TARGET_URL, DISPLAY_ORDER, CONTENT);

        banner.deactivate();
        banner.activate();

        assertTrue(banner.getIsActive());
    }

    @Test
    void updateBannerTrimsStrings() {
        Banner banner = Banner.create(
                createTitle(), BannerType.MAIN, createPeriod(), createImage(),
                " originalUrl ", 1, " originalContent "
        );

        banner.updateBanner(
                BannerTitle.of("New Title"), BannerType.ROUTES, createPeriod(), createImage(),
                " updatedUrl ", 2, " updatedContent "
        );

        assertEquals("updatedUrl", banner.getTargetUrl());
        assertEquals("updatedContent", banner.getContent());
    }

    @Test
    void createBannerWithNullDisplayOrderUsesDefault() {
        Banner banner = Banner.create(createTitle(), BannerType.MAIN, createPeriod(), createImage(), TARGET_URL, null, CONTENT);
        assertEquals(0, banner.getDisplayOrder());
    }

    @Test
    void restoreBannerWithVersionUsesDefault() {
        BannerId id = BannerId.of(BANNER_ID);
        BannerTitle title = createTitle();
        BannerType type = BannerType.MAIN;
        BannerPeriod period = createPeriod();
        BannerImage image = createImage();
        Banner banner = Banner.restore(id, title, type, period, image,
                TARGET_URL, IS_ACTIVE, DISPLAY_ORDER, CONTENT, CREATED_AT, UPDATED_AT, null);

        assertNotNull(banner);
        assertNotNull(banner.getId());
        assertEquals(TITLE, banner.getTitle().getValue());
        assertEquals(BANNER_ID, banner.getId().getValue());
        assertEquals(type, banner.getType());
        assertTrue(period.getStartTime().isEqual(banner.getPeriod().getStartTime()));
        assertTrue(period.getEndTime().isEqual(banner.getPeriod().getEndTime()));
        assertEquals(TARGET_URL, banner.getTargetUrl());
        assertTrue(banner.getIsActive());
        assertEquals(DISPLAY_ORDER, banner.getDisplayOrder());
        assertNotNull(banner.getCreatedAt());
        assertNotNull(banner.getUpdatedAt());
        assertEquals(0L, banner.getVersion());
        assertEquals(CONTENT, banner.getContent());
        assertEquals(IMAGE_PATH, banner.getImageUrl().getValue());
    }
}