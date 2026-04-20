package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.model.StopFavorite;
import biz.ugur.busroutebackend.client.domain.repository.StopFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.exceptions.BusStopNotFoundException;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class AddStopToFavoritesUseCaseTest {

    @InjectMocks
    private AddStopToFavoritesUseCase useCase;

    @Mock
    private StopFavoriteRepository stopFavoriteRepository;

    @Mock
    private BusStopRepository busStopRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void addsStopFavoriteWhenNotExists() {
        ClientId clientId = ClientId.generate();
        BusStopId stopId = BusStopId.generate();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(stopFavoriteRepository.existsByClientIdAndStopId(clientId, stopId)).thenReturn(Mono.just(false));
        when(busStopRepository.existsById(stopId)).thenReturn(Mono.just(true));
        when(stopFavoriteRepository.save(any(StopFavorite.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(
                new AddStopToFavoritesUseCase.Command(clientId.getValue(), stopId.getValue()))))
                .assertNext(result -> assertTrue(result))
                .verifyComplete();

        verify(stopFavoriteRepository).save(any(StopFavorite.class));
    }

    @Test
    void removesStopFavoriteWhenAlreadyExists() {
        ClientId clientId = ClientId.generate();
        BusStopId stopId = BusStopId.generate();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(stopFavoriteRepository.existsByClientIdAndStopId(clientId, stopId)).thenReturn(Mono.just(true));
        when(stopFavoriteRepository.deleteByClientIdAndStopId(clientId, stopId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new AddStopToFavoritesUseCase.Command(clientId.getValue(), stopId.getValue()))))
                .assertNext(result -> assertFalse(result))
                .verifyComplete();
    }

    @Test
    void errorsWhenStopNotFoundOnAdd() {
        ClientId clientId = ClientId.generate();
        BusStopId stopId = BusStopId.generate();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(stopFavoriteRepository.existsByClientIdAndStopId(clientId, stopId)).thenReturn(Mono.just(false));
        when(busStopRepository.existsById(stopId)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(Mono.just(
                new AddStopToFavoritesUseCase.Command(clientId.getValue(), stopId.getValue()))))
                .expectErrorSatisfies(err -> assertInstanceOf(BusStopNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesClientBoundContext() {
        assertEquals("client", useCase.getBoundContext());
    }
}
