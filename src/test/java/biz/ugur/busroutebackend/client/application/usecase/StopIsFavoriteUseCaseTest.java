package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.StopFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class StopIsFavoriteUseCaseTest {

    @InjectMocks
    private StopIsFavoriteUseCase useCase;

    @Mock
    private StopFavoriteRepository stopFavoriteRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Test
    void returnsTrueWhenStopFavoriteExists() {
        ClientId clientId = ClientId.generate();
        BusStopId stopId = BusStopId.generate();

        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(stopFavoriteRepository.existsByClientIdAndStopId(clientId, stopId)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(
                new StopIsFavoriteUseCase.Request(clientId.getValue(), stopId.getValue())))
                .assertNext(result -> assertTrue(result))
                .verifyComplete();
    }

    @Test
    void returnsFalseWhenStopFavoriteDoesNotExist() {
        ClientId clientId = ClientId.generate();
        BusStopId stopId = BusStopId.generate();

        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(stopFavoriteRepository.existsByClientIdAndStopId(clientId, stopId)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(
                new StopIsFavoriteUseCase.Request(clientId.getValue(), stopId.getValue())))
                .assertNext(result -> assertFalse(result))
                .verifyComplete();
    }
}
