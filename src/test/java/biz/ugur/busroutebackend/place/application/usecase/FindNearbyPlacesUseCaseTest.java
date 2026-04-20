package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.PlaceNearbyQuery;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchResult;
import biz.ugur.busroutebackend.place.domain.repository.PlaceSearchRepository;
import biz.ugur.busroutebackend.place.infrastructure.config.PlaceSearchProperties;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class FindNearbyPlacesUseCaseTest {

    @InjectMocks
    private FindNearbyPlacesUseCase useCase;

    @Mock
    private PlaceSearchRepository placeSearchRepository;

    @Spy
    private PlaceSearchProperties properties = new PlaceSearchProperties();

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void usesDefaultRadiusWhenRadiusZero() {
        PlaceSearchResult result = mock(PlaceSearchResult.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placeSearchRepository.searchNearby(anyDouble(), anyDouble(), anyInt(), any(), anyInt()))
                .thenReturn(Flux.just(result));

        StepVerifier.create(useCase.execute(Mono.just(
                new PlaceNearbyQuery(37.96, 58.33, 0, null, 0))))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();

        ArgumentCaptor<Integer> radius = ArgumentCaptor.forClass(Integer.class);
        verify(placeSearchRepository).searchNearby(anyDouble(), anyDouble(), radius.capture(), any(), anyInt());
        assertEquals(500, radius.getValue());
    }

    @Test
    void capsRadiusAtMaximum() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placeSearchRepository.searchNearby(anyDouble(), anyDouble(), anyInt(), any(), anyInt()))
                .thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new PlaceNearbyQuery(37.96, 58.33, 100000, "MALL", 100))))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Integer> radius = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(placeSearchRepository).searchNearby(anyDouble(), anyDouble(), radius.capture(), anyString(), limit.capture());
        assertEquals(5000, radius.getValue());
        assertEquals(50, limit.getValue());
    }

    @Test
    void exposesPlaceBoundContext() {
        assertEquals("place", useCase.getBoundContext());
    }
}
