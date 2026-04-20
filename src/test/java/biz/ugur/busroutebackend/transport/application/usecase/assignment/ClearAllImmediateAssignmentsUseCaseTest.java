package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class ClearAllImmediateAssignmentsUseCaseTest {

    @InjectMocks
    private ClearAllImmediateAssignmentsUseCase useCase;

    @Mock
    private RouteAssignmentRepository assignmentRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void deletesAllImmediateAssignmentsForCurrentShift() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findActiveByDateAndShift(any(LocalDate.class), any(ShiftType.class)))
                .thenReturn(Flux.empty());
        when(assignmentRepository.deleteByEffectiveDateAndShift(any(LocalDate.class), any(ShiftType.class)))
                .thenReturn(Mono.just(5));

        StepVerifier.create(useCase.execute(Mono.empty()))
                .assertNext(result -> {
                    assertEquals(5, result.clearedCount());
                    assertTrue(result.success());
                })
                .verifyComplete();
    }

    @Test
    void returnsFailureWhenRepositoryErrors() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findActiveByDateAndShift(any(LocalDate.class), any(ShiftType.class)))
                .thenReturn(Flux.error(new RuntimeException("db down")));

        StepVerifier.create(useCase.execute(Mono.empty()))
                .assertNext(result -> {
                    assertEquals(0, result.clearedCount());
                    assertEquals(false, result.success());
                    assertEquals("db down", result.errorMessage());
                })
                .verifyComplete();
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
