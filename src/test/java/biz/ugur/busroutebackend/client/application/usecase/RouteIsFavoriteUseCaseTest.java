package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.RouteFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
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
class RouteIsFavoriteUseCaseTest {

    @InjectMocks
    private RouteIsFavoriteUseCase useCase;

    @Mock
    private RouteFavoriteRepository routeFavoriteRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Test
    void returnsTrueWhenRouteFavoriteExists() {
        ClientId clientId = ClientId.generate();
        BusRouteId routeId = BusRouteId.generate();

        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(routeFavoriteRepository.existsByClientIdAndRouteId(clientId, routeId)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(
                new RouteIsFavoriteUseCase.Request(clientId.getValue(), routeId.getValue())))
                .assertNext(result -> assertTrue(result))
                .verifyComplete();
    }

    @Test
    void returnsFalseWhenRouteFavoriteDoesNotExist() {
        ClientId clientId = ClientId.generate();
        BusRouteId routeId = BusRouteId.generate();

        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(routeFavoriteRepository.existsByClientIdAndRouteId(clientId, routeId)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(
                new RouteIsFavoriteUseCase.Request(clientId.getValue(), routeId.getValue())))
                .assertNext(result -> assertFalse(result))
                .verifyComplete();
    }
}
