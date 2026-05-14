package biz.ugur.busroutebackend.advertising.application.usecase.client;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetActiveAdsUseCaseTest {

    @InjectMocks
    private GetActiveAdsUseCase useCase;

    @Mock
    private AdPlacementRepository placementRepository;

    @Mock
    private AdPlacementTargetRepository targetRepository;

    @Mock
    private AdPlacementResponseMapper responseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private AdPlacement buildPlacement() {
        return AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "Title", "content", null, null, null, null, null, 0);
    }

    private void setupCorrelation() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void returnsAdsFromRepository() {
        AdPlacement ad = buildPlacement();
        AdPlacementResponse response = mock(AdPlacementResponse.class);

        setupCorrelation();
        when(placementRepository.findActiveByTypeAt(any(PlacementType.class), any(LocalDateTime.class)))
                .thenReturn(Flux.just(ad));
        when(responseMapper.toResponses(List.of(ad))).thenReturn(Flux.just(response));

        StepVerifier.create(useCase.execute(new GetActiveAdsUseCase.Query(PlacementType.BANNER, null)))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }

    @Test
    void returnsEmptyListWhenRepositoryIsEmpty() {
        setupCorrelation();
        when(placementRepository.findActiveByTypeAt(any(PlacementType.class), any(LocalDateTime.class)))
                .thenReturn(Flux.empty());
        when(responseMapper.toResponses(List.of())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(new GetActiveAdsUseCase.Query(PlacementType.BANNER, null)))
                .assertNext(list -> assertEquals(0, list.size()))
                .verifyComplete();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("advertising.client", useCase.getBoundContext());
    }

    @Test
    void filtersByContextHome_returnsOnlyPlacementsWithHomeTarget() {
        AdPlacement placementHome = buildPlacement();
        AdPlacement placementRoutes = buildPlacement();
        PlacementId idHome = placementHome.getId();
        PlacementId idRoutes = placementRoutes.getId();
        AdPlacementResponse response = mock(AdPlacementResponse.class);

        setupCorrelation();
        when(placementRepository.findActiveByTypeAt(any(PlacementType.class), any(LocalDateTime.class)))
                .thenReturn(Flux.just(placementHome, placementRoutes));
        when(targetRepository.findByPlacementIds(anyCollection()))
                .thenReturn(Mono.just(Map.of(
                        idHome.getValue(), List.of(PlacementTarget.general(TargetType.HOME)),
                        idRoutes.getValue(), List.of(PlacementTarget.general(TargetType.ROUTES_LIST))
                )));
        when(responseMapper.toResponses(anyList()))
                .thenAnswer(inv -> {
                    List<AdPlacement> list = inv.getArgument(0);
                    return Flux.fromIterable(list).map(p -> response);
                });

        StepVerifier.create(useCase.execute(new GetActiveAdsUseCase.Query(PlacementType.BANNER, "home")))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }

    @Test
    void noContext_returnsAllActive() {
        AdPlacement p1 = buildPlacement();
        AdPlacement p2 = buildPlacement();
        AdPlacementResponse response = mock(AdPlacementResponse.class);

        setupCorrelation();
        when(placementRepository.findActiveByTypeAt(any(PlacementType.class), any(LocalDateTime.class)))
                .thenReturn(Flux.just(p1, p2));
        when(responseMapper.toResponses(anyList()))
                .thenAnswer(inv -> {
                    List<AdPlacement> list = inv.getArgument(0);
                    return Flux.fromIterable(list).map(p -> response);
                });

        StepVerifier.create(useCase.execute(new GetActiveAdsUseCase.Query(PlacementType.BANNER, null)))
                .assertNext(list -> assertEquals(2, list.size()))
                .verifyComplete();
    }

    @Test
    void unknownContext_returnsAllActive() {
        AdPlacement p1 = buildPlacement();
        AdPlacement p2 = buildPlacement();
        AdPlacementResponse response = mock(AdPlacementResponse.class);

        setupCorrelation();
        when(placementRepository.findActiveByTypeAt(any(PlacementType.class), any(LocalDateTime.class)))
                .thenReturn(Flux.just(p1, p2));
        when(responseMapper.toResponses(anyList()))
                .thenAnswer(inv -> {
                    List<AdPlacement> list = inv.getArgument(0);
                    return Flux.fromIterable(list).map(p -> response);
                });

        StepVerifier.create(useCase.execute(new GetActiveAdsUseCase.Query(PlacementType.BANNER, "unknown_screen")))
                .assertNext(list -> assertEquals(2, list.size()))
                .verifyComplete();
    }
}
