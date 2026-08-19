package biz.ugur.busroutebackend.advertising.application.usecase.integration;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WithdrawExternalBannerUseCaseTest {

    private static final String SERVICE_ID = "svc-1";
    private static final String EXTERNAL_REF = "banner-42";

    @Mock
    private AdPlacementRepository placementRepository;
    @Mock
    private SecurityContextService securityService;
    @Mock
    private CorrelationContextService correlationService;
    @Mock
    private EventBus eventBus;

    private WithdrawExternalBannerUseCase useCase;

    @BeforeEach
    void setUp() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementRepository.save(any(AdPlacement.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(securityService.logAudit(anyString(), anyString(), anyString())).thenReturn(Mono.empty());
        useCase = new WithdrawExternalBannerUseCase(placementRepository, securityService,
                correlationService, eventBus);
    }

    private static AdPlacement withStatus(PlacementStatus status) {
        return active(SERVICE_ID).toBuilder().status(status).build();
    }

    private static AdPlacement active(String ownerId) {
        AdPlacement placement = AdPlacement.createExternal(ownerId, EXTERNAL_REF, PlacementType.BANNER,
                "Внешний", null, "https://cdn/i.png", "https://t", null, ContentType.LINK,
                PlacementWindow.of(LocalDateTime.now(), LocalDateTime.now().plusDays(3)),
                List.of(PlacementTarget.general(TargetType.ROUTES_LIST)), 1);
        return placement.toBuilder().status(PlacementStatus.ACTIVE).build();
    }

    @Test
    void ownerWithdrawsOwnBanner() {
        when(placementRepository.findByExternalRef(SERVICE_ID, EXTERNAL_REF))
                .thenReturn(Mono.just(active(SERVICE_ID)));

        StepVerifier.create(useCase.execute(Mono.just(
                        new WithdrawExternalBannerUseCase.Command(SERVICE_ID, EXTERNAL_REF))))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<AdPlacement> saved = ArgumentCaptor.forClass(AdPlacement.class);
        verify(placementRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PlacementStatus.PAUSED);
    }

    @Test
    void foreignBannerIsNotWithdrawn() {
        when(placementRepository.findByExternalRef(SERVICE_ID, EXTERNAL_REF))
                .thenReturn(Mono.just(active("svc-other")));

        StepVerifier.create(useCase.execute(Mono.just(
                        new WithdrawExternalBannerUseCase.Command(SERVICE_ID, EXTERNAL_REF))))
                .expectError(AdvertisingValidationException.class)
                .verify();

        verify(placementRepository, never()).save(any());
    }

    @Test
    void unknownReferenceIsReportedAsError() {
        when(placementRepository.findByExternalRef(SERVICE_ID, "no-such"))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                        new WithdrawExternalBannerUseCase.Command(SERVICE_ID, "no-such"))))
                .expectError(AdvertisingValidationException.class)
                .verify();
    }

    @Test
    void withdrawalIsAudited() {
        when(placementRepository.findByExternalRef(SERVICE_ID, EXTERNAL_REF))
                .thenReturn(Mono.just(active(SERVICE_ID)));

        StepVerifier.create(useCase.execute(Mono.just(
                        new WithdrawExternalBannerUseCase.Command(SERVICE_ID, EXTERNAL_REF))))
                .expectNextCount(1)
                .verifyComplete();

        verify(securityService).logAudit(anyString(), anyString(), anyString());
    }
    @Test
    void bannerWithdrawnBeforeItStartsIsCancelledInsteadOfFailing() {
        when(placementRepository.findByExternalRef(SERVICE_ID, EXTERNAL_REF))
                .thenReturn(Mono.just(withStatus(PlacementStatus.SCHEDULED)));

        StepVerifier.create(useCase.execute(Mono.just(new WithdrawExternalBannerUseCase.Command(SERVICE_ID, EXTERNAL_REF))))
                .assertNext(result -> assertThat(result.getStatus()).isEqualTo(PlacementStatus.CANCELLED))
                .verifyComplete();
    }

    @Test
    void repeatedWithdrawalOfTheSameBannerIsAccepted() {
        when(placementRepository.findByExternalRef(SERVICE_ID, EXTERNAL_REF))
                .thenReturn(Mono.just(withStatus(PlacementStatus.PAUSED)));

        StepVerifier.create(useCase.execute(Mono.just(new WithdrawExternalBannerUseCase.Command(SERVICE_ID, EXTERNAL_REF))))
                .assertNext(result -> assertThat(result.getStatus()).isEqualTo(PlacementStatus.PAUSED))
                .verifyComplete();
    }

    @Test
    void withdrawingAFinishedBannerDoesNotFail() {
        when(placementRepository.findByExternalRef(SERVICE_ID, EXTERNAL_REF))
                .thenReturn(Mono.just(withStatus(PlacementStatus.EXPIRED)));

        StepVerifier.create(useCase.execute(Mono.just(new WithdrawExternalBannerUseCase.Command(SERVICE_ID, EXTERNAL_REF))))
                .assertNext(result -> assertThat(result.getStatus()).isEqualTo(PlacementStatus.EXPIRED))
                .verifyComplete();
    }
}
