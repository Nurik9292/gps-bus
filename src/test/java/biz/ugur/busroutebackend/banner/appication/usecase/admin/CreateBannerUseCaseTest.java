package biz.ugur.busroutebackend.banner.appication.usecase.admin;

import biz.ugur.busroutebackend.banner.appication.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.appication.dto.CreateBannerCommand;
import biz.ugur.busroutebackend.banner.appication.factory.BannerFactory;
import biz.ugur.busroutebackend.banner.appication.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.appication.processor.BannerImageProcessor;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateBannerUseCaseTest {

    private final static String TITLE = "title";
    private final static String CONTENT = "content";
    private final static String TARGET_URL = "targetUrl";
    private final static String IMAGE_URL = "imageUrl";
    private final static String PROCESSED_IMAGE_URL = "processedImageUrl";
    private final static int DISPLAY_ORDER = 1;

    @InjectMocks
    private CreateBannerUseCase createBannerUseCase;

    @Mock
    private BannerImageProcessor bannerImageProcessor;

    @Mock
    private BannerFactory bannerFactory;

    @Mock
    private AdminBannerRepository adminBannerRepository;

    @Mock
    private BannerResponseMapper bannerResponseMapper;

    @Captor
    private ArgumentCaptor<Banner> bannerCaptor;

    @Mock
    private CorrelationContextService correlationService;

    @Test
    void processSuccessFully() {
        CreateBannerCommand command = CreateBannerCommand.builder()
                .title(TITLE)
                .imageUrl(IMAGE_URL)
                .type(BannerType.MAIN.getValue())
                .content(CONTENT)
                .displayOrder(DISPLAY_ORDER)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(1))
                .targetUrl(TARGET_URL)
                .build();

        Banner banner = Banner.create(BannerTitle.of(TITLE),
                BannerType.POPUP,
                BannerPeriod.between(LocalDateTime.now(), LocalDateTime.now().plusDays(1)),
                BannerImage.of(IMAGE_URL),
                TARGET_URL,
                DISPLAY_ORDER,
                CONTENT
                );


       BannerResponse bannerResponse = new BannerResponse(
               banner.getId().getValue(),
               banner.getTitle().getValue(),
               banner.getType().getValue(),
               banner.getImageUrl().getValue(),
               banner.getTargetUrl(),
               banner.getIsActive(),
               banner.getDisplayOrder(),
               banner.getPeriod().getStartTime(),
               banner.getPeriod().getEndTime(),
               banner.getContent()
       );

       when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
       when(bannerImageProcessor.process(IMAGE_URL)).thenReturn(Mono.just(PROCESSED_IMAGE_URL));
       when(bannerFactory.create(any(), any())).thenReturn(Mono.just(banner));
       when(adminBannerRepository.save(banner)).thenReturn(Mono.just(banner));
       when(bannerResponseMapper.toResponse(banner)).thenReturn(Mono.just(bannerResponse));

       Mono<BannerResponse> response = createBannerUseCase.process(Mono.just(command));

        StepVerifier.create(response)
                .assertNext(res -> {
                    assertEquals(banner.getId().getValue(), res.getId());
                    assertEquals(banner.getTitle().getValue(), res.getTitle());
                    assertEquals(banner.getType().getValue(), res.getType());
                    assertEquals(banner.getImageUrl().getValue(), res.getImageUrl());
                    assertEquals(banner.getTargetUrl(), res.getTargetUrl());
                    assertEquals(banner.getIsActive(), res.getIsActive());
                    assertEquals(banner.getDisplayOrder(), res.getDisplayOrder());
                    assertEquals(
                            banner.getPeriod().getStartTime().atZone(ZoneId.systemDefault()).toInstant(),
                            res.getStartDate()
                    );
                    assertEquals(
                            banner.getPeriod().getEndTime().atZone(ZoneId.systemDefault()).toInstant(),
                            res.getEndDate()
                    );
                    assertEquals(banner.getContent(), res.getContent());
                })
                .verifyComplete();

       verify(bannerImageProcessor, times(1)).process(IMAGE_URL);
       verify(adminBannerRepository, times(1)).save(bannerCaptor.capture());

       assertNotNull(bannerCaptor.getValue());
    }

    @Test
    void processFailsWhenImageProcessorThrowsError() {
        CreateBannerCommand command = CreateBannerCommand.builder()
                .title(TITLE)
                .imageUrl(IMAGE_URL)
                .type(BannerType.MAIN.getValue())
                .content(CONTENT)
                .displayOrder(1)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(1))
                .targetUrl(TARGET_URL)
                .build();

        when(correlationService.getCurrentCorrelationId())
                .thenReturn(Mono.just(CorrelationId.generate()));

        when(bannerImageProcessor.process(IMAGE_URL))
                .thenReturn(Mono.error(new RuntimeException("Processing failed")));

        Mono<BannerResponse> result = createBannerUseCase.process(Mono.just(command));

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().equals("Processing failed"))
                .verify();

        verify(bannerImageProcessor, times(1)).process(IMAGE_URL);

        verify(adminBannerRepository, never()).save(any());
    }


    @Test
    void getBoundContext() {
        String admin = createBannerUseCase.getBoundContext();
        assertNotNull(admin);
        assertEquals("admin", admin);
    }
}