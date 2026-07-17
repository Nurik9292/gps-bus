package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.application.dto.stop.UpdateStop;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class UpdateBusStopUseCaseTest {

    @InjectMocks
    private UpdateBusStopUseCase useCase;

    @Mock
    private BusStopRepository busStopRepository;

    @Mock
    private biz.ugur.busroutebackend.transport.application.services.NameHistoryRecorder nameHistoryRecorder;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private BusStop existing;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(nameHistoryRecorder.recordStopChanges(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(reactor.core.publisher.Mono.empty());
        existing = BusStop.create(
                "Central Station", "Central EN", "Merkez", StopCode.of("AS001"),
                new BigDecimal("37.96"), new BigDecimal("58.33"),
                true, "ashgabat", "admin-1"
        );
    }

    @Test
    void updatesExistingStopWhenNameSame() {
        UpdateStop cmd = new UpdateStop(
                existing.getId().getValue(),
                "Central Station", "Central EN", "Merkez",
                new BigDecimal("37.96"), new BigDecimal("58.33"),
                true, true, "ashgabat"
        );

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busStopRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(busStopRepository.save(any(BusStop.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(result -> assertEquals("Central Station", result.stopName()))
                .verifyComplete();
    }

    @Test
    void errorsWhenRenameCollides() {
        UpdateStop cmd = new UpdateStop(
                existing.getId().getValue(),
                "Conflicted Name", "EN", "TM",
                new BigDecimal("37.96"), new BigDecimal("58.33"),
                true, false, "ashgabat"
        );

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busStopRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(busStopRepository.existsByStopName("Conflicted Name")).thenReturn(Mono.just(true));
        when(busStopRepository.save(any(BusStop.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void errorsWhenStopNotFound() {
        UpdateStop cmd = new UpdateStop(
                BusStopId.generate().getValue(),
                "X", null, null, new BigDecimal("37.96"), new BigDecimal("58.33"),
                true, false, "city"
        );

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busStopRepository.findById(any(BusStopId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
