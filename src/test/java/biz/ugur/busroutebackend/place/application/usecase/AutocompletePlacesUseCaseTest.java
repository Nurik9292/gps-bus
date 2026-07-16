package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.PlaceAutocompleteResult;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchQuery;
import biz.ugur.busroutebackend.place.domain.repository.PlaceSearchRepository;
import biz.ugur.busroutebackend.place.infrastructure.cache.PlaceSearchCacheService;
import biz.ugur.busroutebackend.place.infrastructure.config.PlaceSearchProperties;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class AutocompletePlacesUseCaseTest {

    @InjectMocks
    private AutocompletePlacesUseCase useCase;

    @Mock
    private PlaceSearchRepository placeSearchRepository;

    @Mock
    private PlaceSearchCacheService cacheService;

    @Spy
    private PlaceSearchProperties properties = new PlaceSearchProperties();

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsCachedResultsWhenAvailable() {
        PlaceAutocompleteResult result = mock(PlaceAutocompleteResult.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cacheService.getCachedAutocomplete(anyString())).thenReturn(Mono.just(List.of(result)));
        when(placeSearchRepository.autocomplete(anyString(), any(), anyInt())).thenReturn(Flux.empty());
        when(cacheService.cacheAutocompleteResults(anyString(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new PlaceSearchQuery("cafe", "ashgabat", null, 10))))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }

    @Test
    void queriesRepositoryWhenCacheMissAndCachesResults() {
        PlaceAutocompleteResult result = mock(PlaceAutocompleteResult.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cacheService.getCachedAutocomplete(anyString())).thenReturn(Mono.empty());
        when(placeSearchRepository.autocomplete(anyString(), any(), anyInt())).thenReturn(Flux.just(result));
        when(cacheService.cacheAutocompleteResults(anyString(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new PlaceSearchQuery("cafe", "ashgabat", null, 0))))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();

        verify(cacheService).cacheAutocompleteResults(anyString(), any());
    }

    @Test
    void exposesPlaceBoundContext() {
        assertEquals("place", useCase.getBoundContext());
    }
}
