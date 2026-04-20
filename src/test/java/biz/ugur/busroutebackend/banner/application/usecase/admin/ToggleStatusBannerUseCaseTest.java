package biz.ugur.busroutebackend.banner.application.usecase.admin;

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
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class ToggleStatusBannerUseCaseTest {

    @InjectMocks
    private ToggleStatusBannerUseCase useCase;

    @Mock
    private AdminBannerRepository bannerRepository;

    @Mock
    private BannerResponseMapper bannerResponseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private Banner banner;

    @BeforeEach
    void setUp() {
        banner = Banner.create(
                BannerTitle.of("Title"),
                BannerType.MAIN,
                BannerPeriod.between(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(5)),
                BannerImage.of("/img.jpg"), "https://x.tm", 0, "content", null, true
        );
    }

    @Test
    void activatesBannerWhenActiveTrue() {
        Banner inactive = banner.deactivate();
        BannerResponse response = mock(BannerResponse.class);
        when(response.isActive()).thenReturn(true);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(bannerRepository.findById(banner.getId())).thenReturn(Mono.just(inactive));
        when(bannerRepository.save(any(Banner.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(bannerResponseMapper.toResponse(any(Banner.class))).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(Mono.just(
                new ToggleStatusBannerUseCase.Request(banner.getId().getValue(), true))))
                .assertNext(r -> assertTrue(r.isActive()))
                .verifyComplete();

        ArgumentCaptor<Banner> captor = ArgumentCaptor.forClass(Banner.class);
        verify(bannerRepository).save(captor.capture());
        assertTrue(captor.getValue().getIsActive());
    }

    @Test
    void deactivatesBannerWhenActiveIsNullOrFalse() {
        BannerResponse response = mock(BannerResponse.class);
        when(response.isActive()).thenReturn(false);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(bannerRepository.findById(banner.getId())).thenReturn(Mono.just(banner));
        when(bannerRepository.save(any(Banner.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(bannerResponseMapper.toResponse(any(Banner.class))).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(Mono.just(
                new ToggleStatusBannerUseCase.Request(banner.getId().getValue(), null))))
                .assertNext(r -> assertFalse(r.isActive()))
                .verifyComplete();

        ArgumentCaptor<Banner> captor = ArgumentCaptor.forClass(Banner.class);
        verify(bannerRepository).save(captor.capture());
        assertFalse(captor.getValue().getIsActive());
    }

    @Test
    void errorsWhenBannerNotFound() {
        String id = BannerId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(bannerRepository.findById(any(BannerId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new ToggleStatusBannerUseCase.Request(id, true))))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
