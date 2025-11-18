package biz.ugur.busroutebackend.banner.application.usecase.admin;

import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.application.dto.CreateBannerCommand;
import biz.ugur.busroutebackend.banner.application.factory.BannerFactory;
import biz.ugur.busroutebackend.banner.application.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.application.processor.BannerImageProcessor;
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
                CONTENT,
                30,
                true
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
               banner.getContent(),
               banner.getReplyTime(),
               banner.getCreatedAt(),
               banner.getUpdatedAt()
       );

       when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
       when(bannerImageProcessor.process(IMAGE_URL)).thenReturn(Mono.just(PROCESSED_IMAGE_URL));
       when(bannerFactory.create(any(), any())).thenReturn(Mono.just(banner));
       when(adminBannerRepository.save(banner)).thenReturn(Mono.just(banner));
       when(bannerResponseMapper.toResponse(banner)).thenReturn(Mono.just(bannerResponse));

       Mono<BannerResponse> response = createBannerUseCase.process(Mono.just(command));

        StepVerifier.create(response)
                .assertNext(res -> {
                    assertEquals(banner.getId().getValue(), res.id());
                    assertEquals(banner.getTitle().getValue(), res.title());
                    assertEquals(banner.getType().getValue(), res.type());
                    assertEquals(banner.getImageUrl().getValue(), res.imageUrl());
                    assertEquals(banner.getTargetUrl(), res.targetUrl());
                    assertEquals(banner.getIsActive(), res.isActive());
                    assertEquals(banner.getDisplayOrder(), res.displayOrder());
                    assertEquals(banner.getPeriod().getStartTime(), res.startDate());
                    assertEquals(banner.getPeriod().getEndTime(), res.endDate());
                    assertEquals(banner.getContent(), res.content());
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