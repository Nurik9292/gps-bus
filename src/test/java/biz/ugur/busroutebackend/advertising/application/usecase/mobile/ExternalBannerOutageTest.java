package biz.ugur.busroutebackend.advertising.application.usecase.mobile;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementSource;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExternalBannerOutageTest {

    @Mock
    private AdPlacementRepository placementRepository;
    @Mock
    private CorrelationContextService correlationService;
    @Mock
    private EventBus eventBus;

    private GetActiveBannersAsAdPlacementsUseCase useCase;

    @BeforeEach
    void setUp() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        useCase = new GetActiveBannersAsAdPlacementsUseCase(placementRepository, correlationService, eventBus);
    }

    private static AdPlacement externalBanner(String title, int order) {
        return AdPlacement.createExternal("svc-1", "ref-" + order, PlacementType.BANNER,
                title, null, "https://cdn/" + order + ".png", "https://target", null,
                ContentType.LINK,
                PlacementWindow.of(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7)),
                List.of(PlacementTarget.of(TargetType.ROUTES_LIST, null)), order);
    }

    @Test
    void storedBannersKeepShowingWhileOwnerServiceIsUnreachable() {
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.ROUTES_LIST))
                .thenReturn(Flux.just(externalBanner("Переданный ранее", 1)));

        StepVerifier.create(useCase.execute(TargetType.ROUTES_LIST))
                .assertNext(list -> {
                    assertThat(list.getBanners()).hasSize(1);
                    assertThat(list.getBanners().getFirst().title()).isEqualTo("Переданный ранее");
                })
                .verifyComplete();
    }

    @Test
    void externalBannerEndsItsRunByOwnWindowWithoutOwnerParticipation() {
        AdPlacement finished = externalBanner("Отработавший", 1)
                .markAsPendingPayment().markAsScheduled().markAsActive().markAsExpired();

        assertThat(finished.getStatus()).isEqualTo(PlacementStatus.EXPIRED);
        assertThat(finished.getSource()).isEqualTo(PlacementSource.EXTERNAL);
        assertThat(finished.getExternalServiceId()).isEqualTo("svc-1");
    }

    @Test
    void emptyStorageYieldsEmptyZoneRatherThanFailure() {
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.ROUTES_LIST))
                .thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(TargetType.ROUTES_LIST))
                .assertNext(list -> assertThat(list.getBanners()).isEmpty())
                .verifyComplete();
    }
}
