package biz.ugur.busroutebackend.banner.application.usecase.admin;

import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.application.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerNotFoundException;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerImage;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerTitle;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
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
class GetBannerByIdUseCaseTest {

    private static final String BANNER_ID = "bbbd091f-5002-4c37-aa82-dbadb0780b20";
    private static final String TITLE = "Test Banner";
    private static final String TYPE = "main";
    private static final String IMAGE_URL = "/banners/test.jpg";
    private static final String TARGET_URL = "http://test.com";
    private static final String CONTENT = "Test content";
    private static final boolean IS_ACTIVE = true;
    private static final int DISPLAY_ORDER = 1;
    private static final LocalDateTime START_TIME = LocalDateTime.of(2025, 10, 11, 0, 0);
    private static final LocalDateTime END_TIME = LocalDateTime.of(2025, 10, 13, 0, 0);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2025, 10, 10, 0, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2025, 10, 12, 0, 0);

    @InjectMocks
    private GetBannerByIdUseCase getBannerByIdUseCase;

    @Mock
    private AdminBannerRepository adminBannerRepository;

    @Mock
    private BannerResponseMapper bannerResponseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Test
    void shouldGetBannerByIdSuccessfully() {
        // Given
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
        when(banner.getCreatedAt()).thenReturn(CREATED_AT);
        when(banner.getUpdatedAt()).thenReturn(UPDATED_AT);

        BannerResponse bannerResponse = new BannerResponse(
                BANNER_ID,
                TITLE,
                TYPE,
                IMAGE_URL,
                TARGET_URL,
                IS_ACTIVE,
                DISPLAY_ORDER,
                START_TIME,
                END_TIME,
                CONTENT,
                null,
                CREATED_AT,
                UPDATED_AT
        );

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(adminBannerRepository.findById(BannerId.of(BANNER_ID))).thenReturn(Mono.just(banner));
        when(bannerResponseMapper.toResponse(banner)).thenReturn(Mono.just(bannerResponse));

        // When
        Mono<BannerResponse> result = getBannerByIdUseCase.execute(Mono.just(BANNER_ID));

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(BANNER_ID, response.id());
                    assertEquals(TITLE, response.title());
                    assertEquals(TYPE, response.type());
                    assertEquals(IMAGE_URL, response.imageUrl());
                    assertEquals(TARGET_URL, response.targetUrl());
                    assertEquals(IS_ACTIVE, response.isActive());
                    assertEquals(DISPLAY_ORDER, response.displayOrder());
                    assertEquals(CONTENT, response.content());
                })
                .verifyComplete();

        verify(correlationService, times(1)).getCurrentCorrelationId();
        verify(adminBannerRepository, times(1)).findById(BannerId.of(BANNER_ID));
        verify(bannerResponseMapper, times(1)).toResponse(banner);
    }

    @Test
    void shouldThrowExceptionWhenBannerNotFound() {
        // Given
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(adminBannerRepository.findById(BannerId.of(BANNER_ID))).thenReturn(Mono.empty());

        // When
        Mono<BannerResponse> result = getBannerByIdUseCase.execute(Mono.just(BANNER_ID));

        // Then
        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertNotNull(error);
                    assertInstanceOf(BannerNotFoundException.class, error);
                    assertEquals("Banner not found with ID: " + BANNER_ID, error.getMessage());
                })
                .verify();

        verify(correlationService, times(1)).getCurrentCorrelationId();
        verify(adminBannerRepository, times(1)).findById(BannerId.of(BANNER_ID));
        verify(bannerResponseMapper, never()).toResponse(any());
    }

    @Test
    void shouldReturnCorrectBoundContext() {
        // When
        String boundContext = getBannerByIdUseCase.getBoundContext();

        // Then
        assertNotNull(boundContext);
        assertEquals("admin", boundContext);
    }
}
