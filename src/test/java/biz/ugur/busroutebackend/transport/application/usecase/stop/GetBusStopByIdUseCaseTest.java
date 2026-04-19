package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.repository.R2dbcRouteStopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetBusStopByIdUseCaseTest {

    @InjectMocks
    private GetBusStopByIdUseCase useCase;

    @Mock
    private BusStopRepository busStopRepository;

    @Mock
    private R2dbcRouteStopRepository r2dbcRouteStopRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private BusStop stop;

    @BeforeEach
    void setUp() {
        stop = BusStop.create(
                "Central Station", "Central Station en", "Merkez", StopCode.of("AS001"),
                new BigDecimal("37.96"), new BigDecimal("58.33"),
                true, "ashgabat", "admin-1"
        );
    }

    @Test
    void returnsStopDataWhenFound() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busStopRepository.findById(stop.getId())).thenReturn(Mono.just(stop));

        StepVerifier.create(useCase.execute(Mono.just(
                new GetBusStopByIdUseCase.Query(stop.getId().getValue()))))
                .assertNext(data -> assertEquals("Central Station", data.stopName()))
                .verifyComplete();
    }

    @Test
    void completesEmptyWhenStopNotFound() {
        String id = BusStopId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busStopRepository.findById(any(BusStopId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new GetBusStopByIdUseCase.Query(id))))
                .verifyError(RuntimeException.class);
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
