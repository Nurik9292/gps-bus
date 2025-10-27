package biz.ugur.busroutebackend.banner.application.usecase.admin;

import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.application.dto.UpdateBannerCommand;
import biz.ugur.busroutebackend.banner.application.factory.BannerFactory;
import biz.ugur.busroutebackend.banner.application.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.application.processor.BannerImageProcessor;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateBannerUseCaseTest {

    private final static LocalDateTime START_DATE = LocalDateTime.of(2025, 4, 12, 0, 0);
    private final static LocalDateTime END_DATE = LocalDateTime.of(2025, 5, 12, 0, 0);
    private final static String TITLE = "title";
    private final static String CONTENT = "content";
    private final static String BANNER_ID = "banner-id";
    private final static String OLD_IMAGE_URL =  "old-image-url";
    private final static String PROCESSED_IMAGE_URL = "processed-image-url";
    private final static String TARGET_URL = "target-image-url";
    private final static String IMAGE_URL = "image-url";
    private final static int DISPLAY_ORDER = 3;
    private final static String PROCESSED_ERROR = "processing failed";



    @InjectMocks
    private UpdateBannerUseCase updateBannerUseCase;

    @Mock
    private AdminBannerRepository bannerRepository;

    @Mock
    private BannerImageProcessor bannerImageProcessor;

    @Mock
    private BannerFactory bannerFactory;

    @Mock
    private BannerResponseMapper bannerResponseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Captor
    private ArgumentCaptor<Banner> bannerCaptor;

    @Test
    void shouldUpdateBannerSuccessfully() {
        UpdateBannerCommand command = mock(UpdateBannerCommand.class);
        when(command.id()).thenReturn(BANNER_ID);
        when(command.imageUrl()).thenReturn(IMAGE_URL);

        Banner existingBanner = mock(Banner.class);
        when(existingBanner.getImageUrl()).thenReturn(BannerImage.of(OLD_IMAGE_URL));

        Banner updatedBanner = mock(Banner.class);
        Banner savedBanner = mock(Banner.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(bannerRepository.findById(BannerId.of(BANNER_ID))).thenReturn(Mono.just(existingBanner));
        when(bannerImageProcessor.processForUpdate(anyString(), anyString())).thenReturn(Mono.just(PROCESSED_IMAGE_URL));
        when(bannerFactory.update(existingBanner, command, PROCESSED_IMAGE_URL)).thenReturn(Mono.just(updatedBanner));
        when(bannerRepository.save(updatedBanner)).thenReturn(Mono.just(savedBanner));

        BannerResponse bannerResponse = new BannerResponse(
                BANNER_ID,
                TITLE,
                BannerType.MAIN.getValue(),
                PROCESSED_IMAGE_URL,
                TARGET_URL,
                true,
                DISPLAY_ORDER,
                START_DATE,
                END_DATE,
                CONTENT
        );
        when(bannerResponseMapper.toResponse(savedBanner)).thenReturn(Mono.just(bannerResponse));

        Mono<BannerResponse> result = updateBannerUseCase.process(Mono.just(command));

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(BANNER_ID, response.id());
                    assertEquals(TITLE, response.title());
                    assertEquals(PROCESSED_IMAGE_URL, response.imageUrl());
                    assertEquals(TARGET_URL, response.targetUrl());
                    assertTrue(response.isActive());
                    assertEquals(DISPLAY_ORDER, response.displayOrder());
                    assertEquals(CONTENT, response.content());
                    assertEquals(START_DATE, response.startDate());
                    assertEquals(END_DATE, response.endDate());
                })
                .verifyComplete();

        verify(correlationService, times(1)).getCurrentCorrelationId();
        verify(bannerRepository, times(1)).findById(BannerId.of(BANNER_ID));
        verify(bannerImageProcessor, times(1)).processForUpdate(anyString(), anyString());
        verify(bannerFactory, times(1)).update(existingBanner, command, PROCESSED_IMAGE_URL);
        verify(bannerRepository, times(1)).save(bannerCaptor.capture());

        Banner newBanner = bannerCaptor.getValue();

        assertNotNull(newBanner);
    }

    @Test
    void processFailsWhenImageProcessorThrowsError() {
        UpdateBannerCommand command = mock(UpdateBannerCommand.class);
        when(command.id()).thenReturn(BANNER_ID);
        when(command.imageUrl()).thenReturn(IMAGE_URL);

        Banner existingBanner = mock(Banner.class);
        when(existingBanner.getImageUrl()).thenReturn(BannerImage.of(OLD_IMAGE_URL));


        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(bannerRepository.findById(BannerId.of(BANNER_ID))).thenReturn(Mono.just(existingBanner));

        when(bannerImageProcessor.processForUpdate(eq(IMAGE_URL), eq(OLD_IMAGE_URL)))
                .thenReturn(Mono.error(new RuntimeException(PROCESSED_ERROR)));

        Mono<BannerResponse> result = updateBannerUseCase.process(Mono.just(command));

        StepVerifier.create(result)
                .expectErrorMatches(t -> t instanceof RuntimeException && PROCESSED_ERROR.equals(t.getMessage()))
                .verify();

        verify(bannerImageProcessor, times(1)).processForUpdate(anyString(), anyString());

        verify(bannerRepository, never()).save(any());
    }

    @Test
    void getBoundContext() {
        String admin = updateBannerUseCase.getBoundContext();
        assertNotNull(admin);
        assertEquals("admin", admin);
    }
}