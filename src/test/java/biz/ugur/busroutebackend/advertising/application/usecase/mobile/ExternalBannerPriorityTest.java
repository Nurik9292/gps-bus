package biz.ugur.busroutebackend.advertising.application.usecase.mobile;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.banner.application.dto.BannerList;
import biz.ugur.busroutebackend.banner.application.dto.BannerPaginationQuery;
import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
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
class ExternalBannerPriorityTest {

    @Mock
    private AdPlacementRepository placementRepository;
    @Mock
    private CorrelationContextService correlationService;
    @Mock
    private biz.ugur.busroutebackend.shared.application.EventBus eventBus;

    private GetActiveBannersAsAdPlacementsUseCase useCase;

    @BeforeEach
    void setUp() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        useCase = new GetActiveBannersAsAdPlacementsUseCase(placementRepository, correlationService, eventBus);
    }

    private static AdPlacement manual(String title, int order) {
        return AdPlacement.create(null, null, PlacementType.BANNER, PlacementKind.EDITORIAL,
                title, null, "/img.png", "https://t", null, ContentType.LINK,
                PlacementWindow.of(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)),
                List.of(PlacementTarget.general(TargetType.ROUTES_LIST)), order);
    }

    private static AdPlacement external(String title, int order) {
        return AdPlacement.createExternal("svc-1", "ref-" + title, PlacementType.BANNER,
                title, null, "/img.png", "https://t", null, ContentType.LINK,
                PlacementWindow.of(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)),
                List.of(PlacementTarget.general(TargetType.ROUTES_LIST)), order);
    }

    private void givenRoutesBanners(AdPlacement... placements) {
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.ROUTES_LIST))
                .thenReturn(Flux.just(placements));
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.HOME))
                .thenReturn(Flux.empty());
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.STOPS_LIST))
                .thenReturn(Flux.empty());
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.PLACES_LIST))
                .thenReturn(Flux.empty());
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.POPUP))
                .thenReturn(Flux.empty());
    }

    private static List<String> titles(BannerList list) {
        return list.getBanners().stream().map(BannerResponse::title).toList();
    }

    @Test
    void externalBannersTakeFirstThreePositions() {
        givenRoutesBanners(manual("ручной-1", 0), external("внеш-1", 5),
                manual("ручной-2", 1), external("внеш-2", 2), external("внеш-3", 9));

        StepVerifier.create(useCase.execute(TargetType.ROUTES_LIST))
                .assertNext(list -> assertThat(titles(list))
                        .containsExactly("внеш-2", "внеш-1", "внеш-3", "ручной-1", "ручной-2"))
                .verifyComplete();
    }

    @Test
    void fewerThanThreeExternalsLetManualRiseUp() {
        givenRoutesBanners(manual("ручной-1", 0), external("внеш-1", 3), manual("ручной-2", 1));

        StepVerifier.create(useCase.execute(TargetType.ROUTES_LIST))
                .assertNext(list -> assertThat(titles(list))
                        .containsExactly("внеш-1", "ручной-1", "ручной-2"))
                .verifyComplete();
    }

    @Test
    void noExternalsMeansUnchangedOrder() {
        givenRoutesBanners(manual("ручной-1", 0), manual("ручной-2", 1));

        StepVerifier.create(useCase.execute(TargetType.ROUTES_LIST))
                .assertNext(list -> assertThat(titles(list))
                        .containsExactly("ручной-1", "ручной-2"))
                .verifyComplete();
    }

    @Test
    void moreThanThreeExternalsShowOnlyTopThreeByDisplayOrder() {
        givenRoutesBanners(external("внеш-1", 10), external("внеш-2", 1), external("внеш-3", 5),
                external("внеш-4", 7), manual("ручной-1", 0));

        StepVerifier.create(useCase.execute(TargetType.ROUTES_LIST))
                .assertNext(list -> assertThat(titles(list))
                        .containsExactly("внеш-2", "внеш-3", "внеш-4", "ручной-1"))
                .verifyComplete();
    }

    @Test
    void reserveAppliesOnFirstPageOnly() {
        givenRoutesBanners(external("внеш-1", 1), external("внеш-2", 2), external("внеш-3", 3),
                manual("ручной-1", 0), manual("ручной-2", 1));
        BannerPaginationQuery firstPage = BannerPaginationQuery.createWithType(
                1, 3, "display_order", "asc", true, "routes");
        BannerPaginationQuery secondPage = BannerPaginationQuery.createWithType(
                2, 3, "display_order", "asc", true, "routes");

        StepVerifier.create(useCase.executePaginated(firstPage))
                .assertNext(list -> assertThat(titles(list))
                        .containsExactly("внеш-1", "внеш-2", "внеш-3"))
                .verifyComplete();

        StepVerifier.create(useCase.executePaginated(secondPage))
                .assertNext(list -> assertThat(titles(list))
                        .containsExactly("ручной-1", "ручной-2"))
                .verifyComplete();
    }

    @Test
    void otherZonesAreNotAffected() {
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.HOME))
                .thenReturn(Flux.just(manual("главный-1", 0), manual("главный-2", 1)));

        StepVerifier.create(useCase.execute(TargetType.HOME))
                .assertNext(list -> assertThat(titles(list))
                        .containsExactly("главный-1", "главный-2"))
                .verifyComplete();
    }
}
