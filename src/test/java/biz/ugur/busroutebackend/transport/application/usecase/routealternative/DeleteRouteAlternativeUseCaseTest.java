package biz.ugur.busroutebackend.transport.application.usecase.routealternative;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAlternativeRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAlternativeId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class DeleteRouteAlternativeUseCaseTest {

    @InjectMocks
    private DeleteRouteAlternativeUseCase useCase;

    @Mock
    private RouteAlternativeRepository routeAlternativeRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void deletesExistingAlternative() {
        RouteAlternativeId id = RouteAlternativeId.generate();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(routeAlternativeRepository.existsById(id)).thenReturn(Mono.just(true));
        when(routeAlternativeRepository.deleteById(id)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new DeleteRouteAlternativeUseCase.Request(id.getValue())))
                .verifyComplete();

        verify(routeAlternativeRepository).deleteById(id);
    }

    @Test
    void errorsWhenAlternativeNotFound() {
        RouteAlternativeId id = RouteAlternativeId.generate();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(routeAlternativeRepository.existsById(id)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(new DeleteRouteAlternativeUseCase.Request(id.getValue())))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();

        verify(routeAlternativeRepository, never()).deleteById(any(RouteAlternativeId.class));
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
