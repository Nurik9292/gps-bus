package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.repository.RouteFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class AddRouteToFavoritesUseCaseTest {

    @InjectMocks
    private AddRouteToFavoritesUseCase useCase;

    @Mock
    private RouteFavoriteRepository routeFavoriteRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void addsRouteWhenNotAlreadyFavorite() {
        ClientId clientId = ClientId.generate();
        BusRouteId routeId = BusRouteId.generate();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(routeFavoriteRepository.existsByClientIdAndRouteId(clientId, routeId)).thenReturn(Mono.just(false));
        when(routeFavoriteRepository.save(any(RouteFavorite.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(
                new AddRouteToFavoritesUseCase.Command(clientId.getValue(), routeId.getValue()))))
                .assertNext(result -> assertTrue(result))
                .verifyComplete();

        verify(routeFavoriteRepository).save(any(RouteFavorite.class));
    }

    @Test
    void removesRouteWhenAlreadyFavorite() {
        ClientId clientId = ClientId.generate();
        BusRouteId routeId = BusRouteId.generate();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(routeFavoriteRepository.existsByClientIdAndRouteId(clientId, routeId)).thenReturn(Mono.just(true));
        when(routeFavoriteRepository.deleteByClientIdAndRouteId(clientId, routeId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new AddRouteToFavoritesUseCase.Command(clientId.getValue(), routeId.getValue()))))
                .assertNext(result -> assertFalse(result))
                .verifyComplete();

        verify(routeFavoriteRepository).deleteByClientIdAndRouteId(clientId, routeId);
    }

    @Test
    void exposesClientBoundContext() {
        assertEquals("client", useCase.getBoundContext());
    }
}
