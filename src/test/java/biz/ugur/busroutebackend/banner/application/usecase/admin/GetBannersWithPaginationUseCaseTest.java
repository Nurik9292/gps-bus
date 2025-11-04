package biz.ugur.busroutebackend.banner.application.usecase.admin;

import biz.ugur.busroutebackend.banner.application.compresor.DataCompressor;
import biz.ugur.busroutebackend.banner.application.dto.BannerList;
import biz.ugur.busroutebackend.banner.application.dto.BannerPaginationQuery;
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
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class GetBannersWithPaginationUseCaseTest {

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

    private final static String SORT_FIELD = "sortField";
    private final static String SORT_ORDER_DESC = "desc";
    private final static String SORT_ORDER_ASC = "asc";
    private final static int PAGE = 1;
    private final static int SIZE = 10;


    @InjectMocks
    private GetBannersWithPaginationUseCase getBannersWithPaginationUseCase;

    @Mock
    private AdminBannerRepository bannerRepository;

    @Mock
    private BannerResponseMapper bannerResponseMapper;

    @Mock
    private DataCompressor dataCompressor;

    @Mock
    private CorrelationContextService correlationContextService;


    @Test
    void getBannersWithPaginationSuccessFullyOrderByDesc() {
        BannerPaginationQuery query = mock(BannerPaginationQuery.class);
        when(query.getPage()).thenReturn(PAGE);
        when(query.getSize()).thenReturn(SIZE);
        when(query.getSortOrder()).thenReturn(SORT_ORDER_DESC);
        when(query.getSortField()).thenReturn(SORT_FIELD);
        when(query.getActiveOnly()).thenReturn(IS_ACTIVE);

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
                DECOMPRESSOR
        );

        when(correlationContextService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(bannerRepository.findAll(any(Pageable.class))).thenReturn(Flux.just(banner));
        when(bannerRepository.count()).thenReturn(Mono.just(1L));
        when(bannerRepository.countActiveBanners()).thenReturn(Mono.just(100L));
        when(bannerResponseMapper.toResponse(banner)).thenReturn(Mono.just(bannerResponse));

        Mono<BannerList> result = getBannersWithPaginationUseCase.process(Mono.just(query));

        StepVerifier.create(result).assertNext(res -> {
            assertNotNull(res);
            assertEquals(1, res.getBanners().size());
            assertEquals(100L, res.getActiveCount());
            assertFalse(res.pagination().hasNext());
        }).verifyComplete();

        verify(correlationContextService, times(1)).getCurrentCorrelationId();
        verify(bannerRepository, times(1)).findAll(any(Pageable.class));
        verify(bannerRepository, times(1)).countActiveBanners();
        verify(bannerResponseMapper, times(1)).toResponse(banner);
    }


    @Test
    void getBoundContext() {
        String admin = getBannersWithPaginationUseCase.getBoundContext();
        assertNotNull(admin);
        assertEquals("admin", admin);
    }

    @Test
    void getBannersWithPaginationSuccessFullyOrderAsc() {
        BannerPaginationQuery query = mock(BannerPaginationQuery.class);
        when(query.getPage()).thenReturn(PAGE);
        when(query.getSize()).thenReturn(SIZE);
        when(query.getSortOrder()).thenReturn(SORT_ORDER_ASC);
        when(query.getSortField()).thenReturn(SORT_FIELD);
        when(query.getActiveOnly()).thenReturn(IS_ACTIVE);

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
                DECOMPRESSOR
        );

        when(correlationContextService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(bannerRepository.findAll(any(Pageable.class))).thenReturn(Flux.just(banner));
        when(bannerRepository.count()).thenReturn(Mono.just(1L));
        when(bannerRepository.countActiveBanners()).thenReturn(Mono.just(100L));
        when(bannerResponseMapper.toResponse(banner)).thenReturn(Mono.just(bannerResponse));

        Mono<BannerList> result = getBannersWithPaginationUseCase.process(Mono.just(query));

        StepVerifier.create(result).assertNext(Assertions::assertNotNull).verifyComplete();

        verify(correlationContextService, times(1)).getCurrentCorrelationId();
        verify(bannerRepository, times(1)).findAll(any(Pageable.class));
        verify(bannerRepository, times(1)).countActiveBanners();
        verify(bannerResponseMapper, times(1)).toResponse(banner);
    }



    @Test
    void getBannersWithPaginationHasMoreTrue() {
        BannerPaginationQuery query = mock(BannerPaginationQuery.class);
        when(query.getPage()).thenReturn(PAGE);
        when(query.getSize()).thenReturn(1);
        when(query.getSortOrder()).thenReturn(SORT_ORDER_DESC);
        when(query.getSortField()).thenReturn(SORT_FIELD);
        when(query.getActiveOnly()).thenReturn(IS_ACTIVE);

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
                DECOMPRESSOR
        );

        when(correlationContextService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(bannerRepository.findAll(any(Pageable.class))).thenReturn(Flux.just(banner));
        when(bannerRepository.count()).thenReturn(Mono.just(100L));
        when(bannerRepository.countActiveBanners()).thenReturn(Mono.just(100L));
        when(bannerResponseMapper.toResponse(banner)).thenReturn(Mono.just(bannerResponse));

        Mono<BannerList> result = getBannersWithPaginationUseCase.process(Mono.just(query));

        StepVerifier.create(result).assertNext(res -> {
            assertNotNull(res);
            assertEquals(1, res.getBanners().size());
            assertEquals(100L, res.getActiveCount());
            assertTrue(res.pagination().hasNext());
        }).verifyComplete();

        verify(correlationContextService, times(1)).getCurrentCorrelationId();
        verify(bannerRepository, times(1)).findAll(any(Pageable.class));
        verify(bannerRepository, times(1)).countActiveBanners();
        verify(bannerResponseMapper, times(1)).toResponse(banner);
    }

}