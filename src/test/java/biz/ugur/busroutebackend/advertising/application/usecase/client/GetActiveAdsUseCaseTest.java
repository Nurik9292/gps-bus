package biz.ugur.busroutebackend.advertising.application.usecase.client;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
    private AdPlacementResponseMapper responseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsAdsFromRepository() {
        AdPlacement ad = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "Title", "content", null, null, null, null, null, 0);
        AdPlacementResponse response = mock(AdPlacementResponse.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementRepository.findActiveByTypeAt(any(PlacementType.class), any(LocalDateTime.class)))
                .thenReturn(Flux.just(ad));
        when(responseMapper.toResponses(List.of(ad))).thenReturn(Flux.just(response));

        StepVerifier.create(useCase.execute(new GetActiveAdsUseCase.Query(PlacementType.BANNER, null)))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }

    @Test
    void returnsEmptyListWhenRepositoryIsEmpty() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
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
}
