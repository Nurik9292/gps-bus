package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAllRouteAssignmentsUseCaseTest {

    @InjectMocks
    private GetAllRouteAssignmentsUseCase useCase;

    @Mock
    private RouteAssignmentRepository assignmentRepository;

    @Mock
    private RouteAssignmentDataMapper dataMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsAllAssignments() {
        RouteAssignment a = RouteAssignment.create(
                VehicleId.generate(), BusRouteId.generate(),
                LocalDate.now().plusDays(1), ShiftType.FIRST,
                "admin", null, Instant.now().plusSeconds(3600));
        RouteAssignmentData data = mock(RouteAssignmentData.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findAll()).thenReturn(Flux.just(a));
        when(dataMapper.toRouteAssignmentData(a)).thenReturn(Mono.just(data));

        StepVerifier.create(useCase.execute(Mono.empty()))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }

    @Test
    void returnsEmptyListWhenNone() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(Mono.empty()))
                .assertNext(list -> assertEquals(0, list.size()))
                .verifyComplete();
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
