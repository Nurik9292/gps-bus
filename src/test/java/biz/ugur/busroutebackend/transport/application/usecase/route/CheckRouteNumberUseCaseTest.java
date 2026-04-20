package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CheckRouteNumberUseCaseTest {

    @InjectMocks
    private CheckRouteNumberUseCase useCase;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsTrueWhenRouteNumberExists() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.existsByRouteNumber("29A")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(Mono.just("29A")))
                .assertNext(result -> assertTrue(result))
                .verifyComplete();
    }

    @Test
    void returnsFalseWhenRouteNumberDoesNotExist() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.existsByRouteNumber("99X")).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(Mono.just("99X")))
                .assertNext(result -> assertFalse(result))
                .verifyComplete();
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
