package biz.ugur.busroutebackend.advertising.infrastructure.external.tanat;

import biz.ugur.busroutebackend.advertising.application.dto.integration.ExternalBannerCommand;
import biz.ugur.busroutebackend.advertising.application.usecase.integration.UpsertExternalBannerUseCase;
import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TanatBannerSyncServiceTest {

    private static final String SERVICE_ID = "tanat";
    private static final String HASH = "96836879-1e37-4c67-b8d0-786c5e55aa01";

    @Mock
    private TanatBannerApiClient apiClient;
    @Mock
    private UpsertExternalBannerUseCase upsertUseCase;
    @Mock
    private AdPlacementRepository placementRepository;

    private TanatBannerSyncService syncService;

    @BeforeEach
    void setUp() {
        TanatBannerProperties properties = new TanatBannerProperties();
        properties.setServiceId(SERVICE_ID);
        properties.setPositionKey("key-client-pos-app-path");
        properties.setLanguages(List.of("tk"));
        properties.setDevices(List.of("mobile"));
        properties.setOperatingSystems(List.of("android"));

        when(apiClient.downloadImage(anyString())).thenReturn(Mono.just(new byte[]{1, 2, 3}));
        when(upsertUseCase.execute(any())).thenReturn(Mono.just(storedBanner(PlacementStatus.ACTIVE)));
        when(placementRepository.findByExternalServiceId(SERVICE_ID)).thenReturn(Flux.empty());
        when(placementRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        syncService = new TanatBannerSyncService(apiClient, properties, upsertUseCase, placementRepository);
    }

    private static TanatBannerResponse offered() {
        return new TanatBannerResponse(true, "", new TanatBannerResponse.Data(
                new TanatBannerResponse.Banner("tk",
                        "https://tanat.halkarahil.com/api/storage/serve/9d20.png",
                        HASH,
                        "https://tanat.halkarahil.com/api/client-data/client-page?hash=" + HASH)));
    }

    private static TanatBannerResponse nothingOffered() {
        return new TanatBannerResponse(true, "", new TanatBannerResponse.Data(
                new TanatBannerResponse.Banner("", "", null, null)));
    }

    private static AdPlacement storedBanner(PlacementStatus status) {
        AdPlacement placement = AdPlacement.createExternal(SERVICE_ID, HASH, PlacementType.BANNER,
                "tanat " + HASH, null, "/uploads/stored.png", "https://target", null,
                ContentType.LINK,
                PlacementWindow.of(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30)),
                List.of(PlacementTarget.general(TargetType.ROUTES_LIST)), 0);
        return placement.toBuilder().status(status).updatedAt(LocalDateTime.now()).build();
    }

    @Test
    void offeredBannerIsDownloadedAndStoredAsEmbeddedImage() {
        when(apiClient.fetchBanner(anyString(), anyString(), anyString())).thenReturn(Mono.just(offered()));

        StepVerifier.create(syncService.synchronize()).verifyComplete();

        ArgumentCaptor<Mono<ExternalBannerCommand>> captor = ArgumentCaptor.forClass(Mono.class);
        verify(upsertUseCase).execute(captor.capture());
        ExternalBannerCommand command = captor.getValue().block();
        assertThat(command.externalRef()).isEqualTo(HASH);
        assertThat(command.externalServiceId()).isEqualTo(SERVICE_ID);
        assertThat(command.type()).isEqualTo("routes");
        assertThat(command.imageUrl()).startsWith("data:image/png;base64,");
        assertThat(command.targetUrl()).contains("client-page");
    }

    @Test
    void emptyOfferStoresNothing() {
        when(apiClient.fetchBanner(anyString(), anyString(), anyString())).thenReturn(Mono.just(nothingOffered()));

        StepVerifier.create(syncService.synchronize()).verifyComplete();

        verify(upsertUseCase, never()).execute(any());
        verify(apiClient, never()).downloadImage(anyString());
    }

    @Test
    void bannerNoLongerOfferedIsTakenOffAir() {
        when(apiClient.fetchBanner(anyString(), anyString(), anyString())).thenReturn(Mono.just(nothingOffered()));
        when(placementRepository.findByExternalServiceId(SERVICE_ID))
                .thenReturn(Flux.just(storedBanner(PlacementStatus.ACTIVE)));

        StepVerifier.create(syncService.synchronize()).verifyComplete();

        ArgumentCaptor<AdPlacement> captor = ArgumentCaptor.forClass(AdPlacement.class);
        verify(placementRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PlacementStatus.PAUSED);
    }

    @Test
    void stillOfferedBannerIsNotTakenOffAir() {
        when(apiClient.fetchBanner(anyString(), anyString(), anyString())).thenReturn(Mono.just(offered()));
        when(placementRepository.findByExternalServiceId(SERVICE_ID))
                .thenReturn(Flux.just(storedBanner(PlacementStatus.ACTIVE)));

        StepVerifier.create(syncService.synchronize()).verifyComplete();

        verify(placementRepository, never()).save(any());
    }

    @Test
    void tanatOutageLeavesStoredBannersUntouched() {
        when(apiClient.fetchBanner(anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));
        when(placementRepository.findByExternalServiceId(SERVICE_ID))
                .thenReturn(Flux.just(storedBanner(PlacementStatus.ACTIVE)));

        StepVerifier.create(syncService.synchronize()).verifyComplete();

        verify(upsertUseCase, never()).execute(any());
        verify(placementRepository, never()).save(any());
    }
    @Test
    void bannerTakenDownByAdminIsNotBroughtBackByNextSync() {
        when(apiClient.fetchBanner(anyString(), anyString(), anyString())).thenReturn(Mono.just(offered()));
        when(placementRepository.findByExternalServiceId(SERVICE_ID))
                .thenReturn(Flux.just(storedBanner(PlacementStatus.CANCELLED)));

        StepVerifier.create(syncService.synchronize()).verifyComplete();

        verify(upsertUseCase, never()).execute(any());
        verify(apiClient, never()).downloadImage(anyString());
    }

    @Test
    void sameBannerAcrossAllCombinationsIsStoredOnce() {
        TanatBannerProperties everyCombination = new TanatBannerProperties();
        everyCombination.setServiceId(SERVICE_ID);
        everyCombination.setPositionKey("key-client-pos-app-path");
        everyCombination.setLanguages(List.of("tk", "ru", "en"));
        everyCombination.setDevices(List.of("mobile"));
        everyCombination.setOperatingSystems(List.of("android", "ios"));
        TanatBannerSyncService service = new TanatBannerSyncService(
                apiClient, everyCombination, upsertUseCase, placementRepository);
        when(apiClient.fetchBanner(anyString(), anyString(), anyString())).thenReturn(Mono.just(offered()));

        StepVerifier.create(service.synchronize()).verifyComplete();

        verify(apiClient, times(6)).fetchBanner(anyString(), anyString(), anyString());
        verify(upsertUseCase, times(1)).execute(any());
    }
    @Test
    void unchangedBannerIsNotDownloadedAgainSoItsUrlStaysStable() {
        when(apiClient.fetchBanner(anyString(), anyString(), anyString())).thenReturn(Mono.just(offered()));
        when(placementRepository.findByExternalServiceId(SERVICE_ID))
                .thenReturn(Flux.just(storedBanner(PlacementStatus.ACTIVE)));

        StepVerifier.create(syncService.synchronize()).verifyComplete();

        verify(apiClient, never()).downloadImage(anyString());
        verify(upsertUseCase, never()).execute(any());
    }

    @Test
    void bannerWithoutStoredImageIsDownloadedEvenWhenActive() {
        AdPlacement withoutImage = storedBanner(PlacementStatus.ACTIVE).toBuilder().imageUrl(null).build();
        when(apiClient.fetchBanner(anyString(), anyString(), anyString())).thenReturn(Mono.just(offered()));
        when(placementRepository.findByExternalServiceId(SERVICE_ID)).thenReturn(Flux.just(withoutImage));

        StepVerifier.create(syncService.synchronize()).verifyComplete();

        verify(apiClient).downloadImage(anyString());
        verify(upsertUseCase).execute(any());
    }

    @Test
    void bannerWithdrawnEarlierIsDownloadedAgainToComeBackOnAir() {
        when(apiClient.fetchBanner(anyString(), anyString(), anyString())).thenReturn(Mono.just(offered()));
        when(placementRepository.findByExternalServiceId(SERVICE_ID))
                .thenReturn(Flux.just(storedBanner(PlacementStatus.PAUSED)));

        StepVerifier.create(syncService.synchronize()).verifyComplete();

        verify(upsertUseCase).execute(any());
    }
    @Test
    void bannerIsRefreshedOnceADayInCaseTanatSwappedTheImageUnderSameHash() {
        AdPlacement staleCopy = storedBanner(PlacementStatus.ACTIVE).toBuilder()
                .updatedAt(LocalDateTime.now().minusHours(25))
                .build();
        when(apiClient.fetchBanner(anyString(), anyString(), anyString())).thenReturn(Mono.just(offered()));
        when(placementRepository.findByExternalServiceId(SERVICE_ID)).thenReturn(Flux.just(staleCopy));

        StepVerifier.create(syncService.synchronize()).verifyComplete();

        verify(apiClient).downloadImage(anyString());
        verify(upsertUseCase).execute(any());
    }
    @Test
    void storedBannerAlwaysCarriesPeriodBecauseMobileClientCannotParseNulls() {
        when(apiClient.fetchBanner(anyString(), anyString(), anyString())).thenReturn(Mono.just(offered()));

        StepVerifier.create(syncService.synchronize()).verifyComplete();

        ArgumentCaptor<Mono<ExternalBannerCommand>> captor = ArgumentCaptor.forClass(Mono.class);
        verify(upsertUseCase).execute(captor.capture());
        ExternalBannerCommand command = captor.getValue().block();
        assertThat(command.startsAt()).isNotNull();
        assertThat(command.endsAt()).isNotNull();
        assertThat(command.endsAt()).isAfter(LocalDateTime.now().plusYears(1));
    }

    @Test
    void repeatedStoreKeepsOriginalRunStartInsteadOfSlidingItToToday() {
        LocalDateTime startedLongAgo = LocalDateTime.now().minusDays(30);
        AdPlacement stale = storedBanner(PlacementStatus.ACTIVE).toBuilder()
                .updatedAt(LocalDateTime.now().minusHours(25))
                .window(PlacementWindow.of(startedLongAgo, startedLongAgo.plusYears(10)))
                .build();
        when(apiClient.fetchBanner(anyString(), anyString(), anyString())).thenReturn(Mono.just(offered()));
        when(placementRepository.findByExternalServiceId(SERVICE_ID)).thenReturn(Flux.just(stale));

        StepVerifier.create(syncService.synchronize()).verifyComplete();

        ArgumentCaptor<Mono<ExternalBannerCommand>> captor = ArgumentCaptor.forClass(Mono.class);
        verify(upsertUseCase).execute(captor.capture());
        assertThat(captor.getValue().block().startsAt()).isEqualTo(startedLongAgo);
    }
    @Test
    void bannerStoredWithoutPeriodIsRepairedOnNextSync() {
        AdPlacement withoutPeriod = storedBanner(PlacementStatus.ACTIVE).toBuilder()
                .updatedAt(LocalDateTime.now())
                .window(null)
                .build();
        when(apiClient.fetchBanner(anyString(), anyString(), anyString())).thenReturn(Mono.just(offered()));
        when(placementRepository.findByExternalServiceId(SERVICE_ID)).thenReturn(Flux.just(withoutPeriod));

        StepVerifier.create(syncService.synchronize()).verifyComplete();

        ArgumentCaptor<Mono<ExternalBannerCommand>> captor = ArgumentCaptor.forClass(Mono.class);
        verify(upsertUseCase).execute(captor.capture());
        assertThat(captor.getValue().block().startsAt()).isNotNull();
    }
}
