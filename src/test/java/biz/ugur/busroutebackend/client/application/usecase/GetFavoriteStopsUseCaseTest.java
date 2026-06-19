package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.model.StopFavorite;
import biz.ugur.busroutebackend.client.domain.repository.StopFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopData;
import biz.ugur.busroutebackend.transport.application.usecase.stop.GetBusStopByIdUseCase;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetFavoriteStopsUseCaseTest {

    @Mock
    private CorrelationContextService correlationService;
    @Mock
    private StopFavoriteRepository stopFavoriteRepository;
    @Mock
    private GetBusStopByIdUseCase getBusStopByIdUseCase;
    @InjectMocks
    private GetFavoriteStopsUseCase useCase;

    @SuppressWarnings("unchecked")
    private void passthroughCorrelation() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void execute_loadsFavoriteStops_andSkipsUnloadable() {
        passthroughCorrelation();
        when(stopFavoriteRepository.findByClientId(any())).thenReturn(Flux.just(
                StopFavorite.create(ClientId.of("c1"), BusStopId.of("s1")),
                StopFavorite.create(ClientId.of("c1"), BusStopId.of("s2"))));
        StopData loaded = mock(StopData.class);
        when(getBusStopByIdUseCase.execute(any()))
                .thenReturn(Mono.just(loaded))
                .thenReturn(Mono.error(new RuntimeException("stop gone")));

        StepVerifier.create(useCase.execute("c1"))
                .assertNext(stops -> assertThat(stops).containsExactly(loaded))
                .verifyComplete();
    }

    @Test
    void execute_emptyWhenNoFavorites() {
        passthroughCorrelation();
        when(stopFavoriteRepository.findByClientId(any())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("c1"))
                .assertNext(stops -> assertThat(stops).isEmpty())
                .verifyComplete();
    }
}
