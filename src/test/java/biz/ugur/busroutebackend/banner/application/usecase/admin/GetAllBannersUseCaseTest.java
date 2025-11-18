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
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(MockitoExtension.class)
class GetAllBannersUseCaseTest {

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
    private final static LocalDateTime CREATED_AT = LocalDateTime.of(2025, 10, 10, 0, 0);
    private final static LocalDateTime UPDATED_AT = LocalDateTime.of(2025, 10, 12, 0, 0);

    @InjectMocks
    private GetAllBannersUseCase getAllBannersUseCase;

    @Mock
    private AdminBannerRepository adminBannerRepository;

    @Mock
    private BannerResponseMapper bannerResponseMapper;

    @Mock
    private DataCompressor dataCompressor;

    @Mock
    CorrelationContextService correlationContextService;


    @Test
    void getAllBannersIsActiveTrueSuccessFully() {
        Banner banner = mock(Banner.class);

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
                DECOMPRESSOR,
                null,
                CREATED_AT,
                UPDATED_AT
        );

        when(correlationContextService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(adminBannerRepository.findActiveBanners()).thenReturn(Flux.just(banner));
        when(adminBannerRepository.countActiveBanners()).thenReturn(Mono.just(100L));
        when(bannerResponseMapper.toResponse(banner)).thenReturn(Mono.just(bannerResponse));

        Mono<BannerList> result = getAllBannersUseCase.process(Mono.just(true));

        StepVerifier.create(result).assertNext(Assertions::assertNotNull).verifyComplete();

        verify(correlationContextService, times(1)).getCurrentCorrelationId();
        verify(adminBannerRepository, times(1)).findActiveBanners();
        verify(adminBannerRepository, times(1)).countActiveBanners();
        verify(bannerResponseMapper, times(1)).toResponse(banner);
    }

    @Test
    void getAllBannersIsActiveFalseSuccessFully() {
        Banner banner = mock(Banner.class);

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
                DECOMPRESSOR,
                null,
                CREATED_AT,
                UPDATED_AT
        );

        when(correlationContextService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(adminBannerRepository.findAll()).thenReturn(Flux.just(banner));
        when(adminBannerRepository.countActiveBanners()).thenReturn(Mono.just(100L));
        when(bannerResponseMapper.toResponse(banner)).thenReturn(Mono.just(bannerResponse));

        Mono<BannerList> result = getAllBannersUseCase.process(Mono.just(false));

        StepVerifier.create(result).assertNext(Assertions::assertNotNull).verifyComplete();

        verify(correlationContextService, times(1)).getCurrentCorrelationId();
        verify(adminBannerRepository, times(1)).findAll();
        verify(adminBannerRepository, times(1)).countActiveBanners();
        verify(bannerResponseMapper, times(1)).toResponse(banner);
    }

    @Test
    void getBoundContext() {
        String admin = getAllBannersUseCase.getBoundContext();
        assertNotNull(admin);
        assertEquals("admin", admin);
    }

}