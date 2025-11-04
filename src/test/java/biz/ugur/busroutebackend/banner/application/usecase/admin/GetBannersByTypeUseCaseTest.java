package biz.ugur.busroutebackend.banner.application.usecase.admin;

import biz.ugur.busroutebackend.banner.application.compresor.DataCompressor;
import biz.ugur.busroutebackend.banner.application.dto.BannerList;
import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.application.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerImage;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerTitle;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
class GetBannersByTypeUseCaseTest {

    private final static String TYPE = "main";
    private final static String TITLE = "title";
    private final static String CONTENT = "content";
    private final static String BANNER_ID = "bannerId";
    private final static String IMAGE_URL = "imageUrl";
    private final static String TARGET_URL = "targetUrl";
    private final static String DECOMPRESSOR = "decompressor";
    private final static boolean IS_ACTIVE = true;
    private final static int DISPLAY_ORDER = 1;
    private final static LocalDateTime START_TIME = LocalDateTime.of(2025, 10, 11, 0, 0);
    private final static LocalDateTime END_TIME = LocalDateTime.of(2025, 10, 13, 0, 0);

    @InjectMocks
    private GetBannersByTypeUseCase getBannersByTypeUseCase;

    @Mock
    private AdminBannerRepository adminBannerRepository;

    @Mock
    private BannerResponseMapper bannerResponseMapper;

    @Mock
    private DataCompressor dataCompressor;

    @Mock
    private CorrelationContextService correlationService;

    @Test
    void getBannersByTypeSuccessFully() {
        Banner banner = mock(Banner.class);
        BannerPeriod period = mock(BannerPeriod.class);
        when(period.getStartTime()).thenReturn(START_TIME);
        when(period.getEndTime()).thenReturn(END_TIME);
        when(banner.getPeriod()).thenReturn(period);
        when(banner.getId()).thenReturn(BannerId.of(BANNER_ID));
        when(banner.getTitle()).thenReturn(BannerTitle.of(TITLE));
        when(banner.getContent()).thenReturn(CONTENT);
        when(banner.getType()).thenReturn(BannerType.MAIN);
        when(banner.getImageUrl()).thenReturn(BannerImage.of(IMAGE_URL));
        when(banner.getTargetUrl()).thenReturn(TARGET_URL);
        when(banner.getIsActive()).thenReturn(IS_ACTIVE);
        when(banner.getDisplayOrder()).thenReturn(DISPLAY_ORDER);

        BannerResponse bannerResponse = new BannerResponse(
                BANNER_ID,
                TITLE,
                BannerType.MAIN.getValue(),
                IMAGE_URL,
                TARGET_URL,
                IS_ACTIVE,
                DISPLAY_ORDER,
                START_TIME,
                END_TIME,
                DECOMPRESSOR
        );

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(adminBannerRepository.findByTypeAndActive(BannerType.fromValue(TYPE))).thenReturn(Flux.just(banner));
        when(adminBannerRepository.countByType(BannerType.fromValue(TYPE))).thenReturn(Mono.just(100L));
        when(adminBannerRepository.countActiveBanners()).thenReturn(Mono.just(100L));
        when(bannerResponseMapper.toResponse(banner)).thenReturn(Mono.just(bannerResponse));

        Mono<BannerList> result = getBannersByTypeUseCase.process(Mono.just(TYPE));

        StepVerifier.create(result).assertNext(Assertions::assertNotNull).verifyComplete();

        verify(correlationService, times(1)).getCurrentCorrelationId();
        verify(adminBannerRepository, times(1)).findByTypeAndActive(BannerType.fromValue(TYPE));
        verify(adminBannerRepository, times(1)).countByType(BannerType.fromValue(TYPE));
        verify(bannerResponseMapper, times(1)).toResponse(banner);
    }


    @Test
    void getBannersByTypeFail() {
        when(adminBannerRepository.countByType(BannerType.fromValue(TYPE))).thenReturn(Mono.just(100L));
        when(adminBannerRepository.countActiveBanners()).thenReturn(Mono.just(100L));
        when(dataCompressor.decodeAndDecompress(CONTENT)).thenReturn(Mono.just(DECOMPRESSOR));
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(adminBannerRepository.findByTypeAndActive(BannerType.fromValue(TYPE))).thenReturn(Flux.error(new RuntimeException("Error")));

        Mono<BannerList> result = getBannersByTypeUseCase.process(Mono.just(TYPE));

        StepVerifier.create(result).expectErrorSatisfies(e -> {
            assertNotNull(e);
            assertInstanceOf(RuntimeException.class, e);
            assertEquals("Banners not found for type: " + TYPE, e.getMessage());
        }).verify();


        verify(correlationService, times(1)).getCurrentCorrelationId();
        verify(adminBannerRepository, times(1)).findByTypeAndActive(BannerType.fromValue(TYPE));
        // Note: countByType and countActiveBanners ARE called due to eager subscription with zipWith
    }

    @Test
    void getBoundContext() {
        String admin = getBannersByTypeUseCase.getBoundContext();
        assertNotNull(admin);
        assertEquals("admin", admin);
    }
}