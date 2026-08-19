package biz.ugur.busroutebackend.advertising.application.usecase.integration;

import biz.ugur.busroutebackend.advertising.application.dto.integration.ExternalBannerCommand;
import biz.ugur.busroutebackend.advertising.application.processor.AdPlacementImageProcessor;
import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementSource;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
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
import reactor.core.publisher.Flux;
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
class UpsertExternalBannerUseCaseTest {

    private static final String SERVICE_ID = "svc-1";
    private static final String EXTERNAL_REF = "banner-42";
    private static final String EMBEDDED_IMAGE = "data:image/png;base64,iVBORw0KGgo=";
    private static final String STORED_IMAGE = "/uploads/ad-placements/stored.png";

    @Mock
    private AdPlacementRepository placementRepository;
    @Mock
    private AdPlacementTargetRepository targetRepository;
    @Mock
    private AdPlacementImageProcessor imageProcessor;
    @Mock
    private SecurityContextService securityService;
    @Mock
    private CorrelationContextService correlationService;
    @Mock
    private EventBus eventBus;

    private UpsertExternalBannerUseCase useCase;

    @BeforeEach
    void setUp() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementRepository.save(any(AdPlacement.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(placementRepository.findByExternalRef(anyString(), anyString())).thenReturn(Mono.empty());
        when(targetRepository.replaceAll(any(), any())).thenReturn(Mono.empty());
        when(targetRepository.findByPlacementId(any())).thenReturn(Flux.empty());
        when(securityService.logAudit(anyString(), anyString(), anyString())).thenReturn(Mono.empty());
        when(imageProcessor.process(anyString())).thenReturn(Mono.just(STORED_IMAGE));
        when(imageProcessor.processForUpdate(anyString(), any())).thenReturn(Mono.just(STORED_IMAGE));
        useCase = new UpsertExternalBannerUseCase(placementRepository, targetRepository,
                imageProcessor, securityService, correlationService, eventBus);
    }

    private static ExternalBannerCommand command() {
        return new ExternalBannerCommand(SERVICE_ID, EXTERNAL_REF, "routes", "Внешний баннер",
                EMBEDDED_IMAGE, "https://target", null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), 1);
    }

    private static AdPlacement existing() {
        return AdPlacement.createExternal(SERVICE_ID, EXTERNAL_REF, PlacementType.BANNER,
                "Старый заголовок", null, STORED_IMAGE, "https://old", null,
                ContentType.LINK, PlacementWindow.of(LocalDateTime.now(), LocalDateTime.now().plusDays(1)),
                List.of(PlacementTarget.of(TargetType.ROUTES_LIST, null)), 1);
    }

    @Test
    void firstTransferCreatesExternalPlacement() {
        StepVerifier.create(useCase.execute(Mono.just(command())))
                .assertNext(result -> {
                    assertThat(result.getSource()).isEqualTo(PlacementSource.EXTERNAL);
                    assertThat(result.getExternalServiceId()).isEqualTo(SERVICE_ID);
                    assertThat(result.getExternalRef()).isEqualTo(EXTERNAL_REF);
                    assertThat(result.getKind()).isEqualTo(PlacementKind.EDITORIAL);
                })
                .verifyComplete();

        verify(placementRepository).save(any(AdPlacement.class));
    }

    @Test
    void repeatedTransferUpdatesInsteadOfDuplicating() {
        when(placementRepository.findByExternalRef(SERVICE_ID, EXTERNAL_REF))
                .thenReturn(Mono.just(existing()));

        StepVerifier.create(useCase.execute(Mono.just(command())))
                .assertNext(result -> {
                    assertThat(result.getTitle()).isEqualTo("Внешний баннер");
                    assertThat(result.getExternalRef()).isEqualTo(EXTERNAL_REF);
                })
                .verifyComplete();

        ArgumentCaptor<AdPlacement> saved = ArgumentCaptor.forClass(AdPlacement.class);
        verify(placementRepository).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(PlacementSource.EXTERNAL);
        assertThat(saved.getValue().getExternalServiceId()).isEqualTo(SERVICE_ID);
    }

    @Test
    void onlyRoutesTypeIsAccepted() {
        ExternalBannerCommand wrongType = new ExternalBannerCommand(SERVICE_ID, EXTERNAL_REF,
                "main", "Внешний баннер", EMBEDDED_IMAGE, "https://target", null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), 1);

        StepVerifier.create(useCase.execute(Mono.just(wrongType)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(AdvertisingValidationException.class)
                        .hasMessageContaining("routes"))
                .verify();

        verify(placementRepository, never()).save(any());
    }

    @Test
    void foreignPlacementIsNotOverwritten() {
        AdPlacement foreign = AdPlacement.createExternal("svc-other", EXTERNAL_REF,
                PlacementType.BANNER, "Чужой", null, STORED_IMAGE, "https://x", null,
                ContentType.LINK, PlacementWindow.of(LocalDateTime.now(), LocalDateTime.now().plusDays(1)),
                List.of(PlacementTarget.of(TargetType.ROUTES_LIST, null)), 1);
        when(placementRepository.findByExternalRef(SERVICE_ID, EXTERNAL_REF)).thenReturn(Mono.just(foreign));

        StepVerifier.create(useCase.execute(Mono.just(command())))
                .expectError(AdvertisingValidationException.class)
                .verify();

        verify(placementRepository, never()).save(any());
    }

    @Test
    void everyOperationIsAudited() {
        StepVerifier.create(useCase.execute(Mono.just(command())))
                .expectNextCount(1)
                .verifyComplete();

        verify(securityService).logAudit(anyString(), anyString(), anyString());
    }
    @Test
    void bannerOfUnsupportedTypeLeavesNothingBehind() {
        ExternalBannerCommand stopsBanner = new ExternalBannerCommand(SERVICE_ID, EXTERNAL_REF, "stops",
                "Внешний баннер", EMBEDDED_IMAGE, "https://target", null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), 1);

        StepVerifier.create(useCase.execute(Mono.just(stopsBanner)))
                .expectError(AdvertisingValidationException.class)
                .verify();

        verify(placementRepository, never()).save(any());
        verify(targetRepository, never()).replaceAll(any(), any());
        verify(securityService, never()).logAudit(anyString(), anyString(), anyString());
    }

    @Test
    void bannerWithoutTitleLeavesNothingBehind() {
        ExternalBannerCommand untitled = new ExternalBannerCommand(SERVICE_ID, EXTERNAL_REF, "routes",
                "   ", EMBEDDED_IMAGE, "https://target", null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), 1);

        StepVerifier.create(useCase.execute(Mono.just(untitled)))
                .expectError(AdvertisingValidationException.class)
                .verify();

        verify(placementRepository, never()).save(any());
        verify(targetRepository, never()).replaceAll(any(), any());
    }

    @Test
    void manualPlacementIsNeverHijackedByExternalService() {
        AdPlacement manual = AdPlacement.create(
                null, null, PlacementType.BANNER, PlacementKind.EDITORIAL,
                "Ручной баннер", null, STORED_IMAGE, "https://manual", null,
                ContentType.LINK, PlacementWindow.of(LocalDateTime.now(), LocalDateTime.now().plusDays(1)),
                List.of(PlacementTarget.of(TargetType.ROUTES_LIST, null)), 1);
        when(placementRepository.findByExternalRef(SERVICE_ID, EXTERNAL_REF)).thenReturn(Mono.just(manual));

        StepVerifier.create(useCase.execute(Mono.just(command())))
                .expectError(AdvertisingValidationException.class)
                .verify();

        verify(placementRepository, never()).save(any());
    }
    @Test
    void acceptedBannerGoesOnAirImmediatelyWhenItsPeriodHasStarted() {
        StepVerifier.create(useCase.execute(Mono.just(command())))
                .assertNext(result -> assertThat(result.getStatus()).isEqualTo(PlacementStatus.ACTIVE))
                .verifyComplete();
    }

    @Test
    void bannerWithFuturePeriodWaitsScheduledInsteadOfShowingEarly() {
        ExternalBannerCommand future = new ExternalBannerCommand(SERVICE_ID, EXTERNAL_REF, "routes",
                "Будущий баннер", EMBEDDED_IMAGE, "https://target", null,
                LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(10), 1);

        StepVerifier.create(useCase.execute(Mono.just(future)))
                .assertNext(result -> assertThat(result.getStatus()).isEqualTo(PlacementStatus.SCHEDULED))
                .verifyComplete();
    }

    @Test
    void finishedRunIsNotSilentlyResurrectedUnderTheSameReference() {
        AdPlacement finished = existing().markAsPendingPayment().markAsScheduled().markAsActive().markAsExpired();
        when(placementRepository.findByExternalRef(SERVICE_ID, EXTERNAL_REF)).thenReturn(Mono.just(finished));

        StepVerifier.create(useCase.execute(Mono.just(command())))
                .expectError(AdvertisingValidationException.class)
                .verify();

        verify(placementRepository, never()).save(any());
    }

    @Test
    void withdrawnBannerStaysWithdrawnWhenOwnerSendsItAgain() {
        AdPlacement paused = existing().markAsPendingPayment().markAsScheduled().markAsActive().markAsPaused();
        when(placementRepository.findByExternalRef(SERVICE_ID, EXTERNAL_REF)).thenReturn(Mono.just(paused));

        StepVerifier.create(useCase.execute(Mono.just(command())))
                .assertNext(result -> assertThat(result.getStatus()).isEqualTo(PlacementStatus.PAUSED))
                .verifyComplete();
    }
    @Test
    void bannerImageMustArriveAsFileNotAsLinkToTheirStorage() {
        ExternalBannerCommand linked = new ExternalBannerCommand(SERVICE_ID, EXTERNAL_REF, "routes",
                "Внешний баннер", "https://tanat.halkarahil.com/api/storage/serve/f95b283b.jpg",
                "https://target", null, LocalDateTime.now(), LocalDateTime.now().plusDays(7), 1);

        StepVerifier.create(useCase.execute(Mono.just(linked)))
                .expectError(AdvertisingValidationException.class)
                .verify();

        verify(placementRepository, never()).save(any());
    }

    @Test
    void acceptedImageIsStoredOnOurSideSoTheAppNeverCallsTheirDomain() {
        StepVerifier.create(useCase.execute(Mono.just(command())))
                .assertNext(result -> assertThat(result.getImageUrl()).isEqualTo(STORED_IMAGE))
                .verifyComplete();

        verify(imageProcessor).process(EMBEDDED_IMAGE);
    }
}
