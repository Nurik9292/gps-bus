package biz.ugur.busroutebackend.banner.application.factory;

import biz.ugur.busroutebackend.shared.application.compressor.DataCompressor;
import biz.ugur.busroutebackend.banner.application.dto.CreateBannerCommand;
import biz.ugur.busroutebackend.banner.application.dto.UpdateBannerCommand;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(SpringExtension.class)
class BannerFactoryTest {

    private final static String TITLE = "title";
    private final static String CONTENT = "description";
    private final static String TYPE = "main";
    private final static String TARGET_URL = "targetUrl";
    private final static String IMAGE_URL = "imageUrl";
    private final static String COMPRESS_CONTENT =  "compressContent";
    private final static String PROCESS_IMAGE_URL = "processImageUrl";
    private final static int DISPLAY_ORDER = 1;

    private final static String UPDATE_TITLE = "update-title";
    private final static String UPDATE_CONTENT = "update-description";
    private final static String UPDATE_TYPE = "places";
    private final static String UPDATE_TARGET_URL = "update-targetUrl";
    private final static String UPDATE_IMAGE_URL = "update-imageUrl";
    private final static String UPDATE_COMPRESS_CONTENT =  "update-compressContent";
    private final static String UPDATE_PROCESS_IMAGE_URL = "update-processImageUrl";
    private final static int UPDATE_DISPLAY_ORDER = 2;


    private CreateBannerCommand createCommand() {
        return CreateBannerCommand.builder()
                .title(TITLE)
                .content(CONTENT)
                .type(TYPE)
                .displayOrder(DISPLAY_ORDER)
                .targetUrl(TARGET_URL)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(1))
                .imageUrl(IMAGE_URL)
                .build();
    }

    private UpdateBannerCommand updateCommand() {
        return UpdateBannerCommand.builder()
                .title(UPDATE_TITLE)
                .content(UPDATE_CONTENT)
                .type(UPDATE_TYPE)
                .displayOrder(UPDATE_DISPLAY_ORDER)
                .targetUrl(UPDATE_TARGET_URL)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(1))
                .imageUrl(UPDATE_IMAGE_URL)
                .isActive(false)
                .build();
    }

    @InjectMocks
    private BannerFactory bannerFactory;

    @Mock
    private DataCompressor dataCompressor;

    @Test
    void createSuccessFully() {
        when(dataCompressor.compressAndEncode(CONTENT)).thenReturn(Mono.just(COMPRESS_CONTENT));

        StepVerifier.create(bannerFactory.create(createCommand(), PROCESS_IMAGE_URL))
                .assertNext(banner -> {
                    assertNotNull(banner);
                    assertEquals(TITLE, banner.getTitle().getValue());
                    assertEquals(COMPRESS_CONTENT, banner.getContent());
                    assertEquals(PROCESS_IMAGE_URL, banner.getImageUrl().getValue());
                })
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Disabled("Требует доработки логики Mono.empty().defaultIfEmpty()")
    void createWithNullContent() {
        StepVerifier.create(bannerFactory.create(CreateBannerCommand.builder()
                .title(TITLE)
                .content(null)
                .type(TYPE)
                .displayOrder(DISPLAY_ORDER)
                .targetUrl(TARGET_URL)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(1))
                .imageUrl(IMAGE_URL)
                .build(), PROCESS_IMAGE_URL))
                .assertNext(banner -> {
                    assertNotNull(banner);
                    assertNull(banner.getContent());
                    assertEquals(TITLE, banner.getTitle().getValue());
                    assertEquals(PROCESS_IMAGE_URL, banner.getImageUrl().getValue());
                })
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Disabled("Требует доработки логики Mono.empty().defaultIfEmpty()")
    void createSuccessFailValueIsEmpty() {
        when(dataCompressor.compressAndEncode(CONTENT)).thenReturn(Mono.empty());

        StepVerifier.create(bannerFactory.create(createCommand(), PROCESS_IMAGE_URL))
                .assertNext(banner -> {
                    assertNotNull(banner);
                    assertNull(banner.getContent());
                })
                .verifyComplete();
    }


    @Test
    void updateSuccessFully() {
        when(dataCompressor.compressAndEncode(CONTENT)).thenReturn(Mono.just(COMPRESS_CONTENT));
        when(dataCompressor.compressAndEncode(UPDATE_CONTENT)).thenReturn(Mono.just(UPDATE_COMPRESS_CONTENT));

        bannerFactory.create(createCommand(), PROCESS_IMAGE_URL)
                .flatMap(banner -> bannerFactory.update(banner, updateCommand(), UPDATE_PROCESS_IMAGE_URL))
                .as(StepVerifier::create)
                .assertNext(updateBanner -> {
                    assertNotNull(updateBanner);
                    assertEquals(UPDATE_TITLE, updateBanner.getTitle().getValue());
                    assertEquals(UPDATE_COMPRESS_CONTENT, updateBanner.getContent());
                    assertEquals(UPDATE_PROCESS_IMAGE_URL, updateBanner.getImageUrl().getValue());
                    assertFalse(updateBanner.getIsActive());
                })
                .verifyComplete();
    }

    @Test
    void updateBannerIsActiveIsTrueSuccessFully() {
        when(dataCompressor.compressAndEncode(CONTENT)).thenReturn(Mono.just(COMPRESS_CONTENT));
        when(dataCompressor.compressAndEncode(UPDATE_CONTENT)).thenReturn(Mono.just(UPDATE_COMPRESS_CONTENT));

        bannerFactory.create(createCommand(), PROCESS_IMAGE_URL)
                .flatMap(banner -> bannerFactory.update(banner, UpdateBannerCommand.builder()
                        .title(UPDATE_TITLE)
                        .content(UPDATE_CONTENT)
                        .type(UPDATE_TYPE)
                        .displayOrder(UPDATE_DISPLAY_ORDER)
                        .targetUrl(UPDATE_TARGET_URL)
                        .startDate(LocalDateTime.now())
                        .endDate(LocalDateTime.now().plusDays(1))
                        .imageUrl(UPDATE_IMAGE_URL)
                        .isActive(true)
                        .build(), UPDATE_PROCESS_IMAGE_URL))
                .as(StepVerifier::create)
                .assertNext(updateBanner -> {
                    assertNotNull(updateBanner);
                    assertEquals(UPDATE_TITLE, updateBanner.getTitle().getValue());
                    assertEquals(UPDATE_COMPRESS_CONTENT, updateBanner.getContent());
                    assertEquals(UPDATE_PROCESS_IMAGE_URL, updateBanner.getImageUrl().getValue());
                    assertTrue(updateBanner.getIsActive());
                })
                .verifyComplete();
    }

    @Test
    void updateWithNullContent() {
        when(dataCompressor.compressAndEncode(CONTENT)).thenReturn(Mono.just(COMPRESS_CONTENT));

        bannerFactory.create(createCommand(), PROCESS_IMAGE_URL)
                .flatMap(banner -> bannerFactory.update(banner, UpdateBannerCommand.builder()
                        .title(UPDATE_TITLE)
                        .content(null)
                        .type(UPDATE_TYPE)
                        .displayOrder(UPDATE_DISPLAY_ORDER)
                        .targetUrl(UPDATE_TARGET_URL)
                        .startDate(LocalDateTime.now())
                        .endDate(LocalDateTime.now().plusDays(1))
                        .imageUrl(UPDATE_IMAGE_URL)
                        .isActive(true)
                        .build(), UPDATE_PROCESS_IMAGE_URL))
                .as(StepVerifier::create)
                .assertNext(updateBanner -> {
                    assertNotNull(updateBanner);
                    assertEquals(COMPRESS_CONTENT, updateBanner.getContent());
                    assertEquals(UPDATE_TITLE, updateBanner.getTitle().getValue());
                    assertEquals(UPDATE_PROCESS_IMAGE_URL, updateBanner.getImageUrl().getValue());
                })
                .verifyComplete();
    }
}