package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.application.dto.stop.CreateStop;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CreateBusStopUseCaseTest {

    @InjectMocks
    private CreateBusStopUseCase useCase;

    @Mock
    private BusStopRepository busStopRepository;

    @Mock
    private SecurityContextService securityContextService;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void createsStopWhenNameUnique() {
        CreateStop cmd = new CreateStop(
                "Central Station", "Central EN", "Merkez",
                new BigDecimal("37.96"), new BigDecimal("58.33"),
                true, true, "ashgabat");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busStopRepository.existsByStopName("Central Station")).thenReturn(Mono.just(false));
        when(securityContextService.getCurrentUsername()).thenReturn(Mono.just("admin"));
        when(busStopRepository.save(any(BusStop.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(result -> assertEquals("Central Station", result.stopName()))
                .verifyComplete();
    }

    @Test
    void errorsWhenStopNameAlreadyExists() {
        CreateStop cmd = new CreateStop(
                "Existing", "EN", "TM",
                new BigDecimal("37.96"), new BigDecimal("58.33"),
                false, true, "ashgabat");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busStopRepository.existsByStopName("Existing")).thenReturn(Mono.just(true));
        when(securityContextService.getCurrentUsername()).thenReturn(Mono.just("admin"));
        when(busStopRepository.save(any(BusStop.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
