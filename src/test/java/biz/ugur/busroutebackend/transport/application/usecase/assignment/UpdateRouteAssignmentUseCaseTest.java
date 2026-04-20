package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.application.dto.assignment.UpdateRouteAssignmentCommand;
import biz.ugur.busroutebackend.transport.application.mapper.RouteAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.exceptions.RouteAssignmentNotFoundException;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class UpdateRouteAssignmentUseCaseTest {

    @InjectMocks
    private UpdateRouteAssignmentUseCase useCase;

    @Mock
    private RouteAssignmentRepository assignmentRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private RouteAssignmentDataMapper dataMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void updatesReasonAndPassesThroughMapper() {
        RouteAssignment existing = RouteAssignment.create(
                VehicleId.generate(), BusRouteId.generate(),
                LocalDate.now().plusDays(5), ShiftType.FIRST,
                "admin", "old reason", Instant.now().plusSeconds(3600));
        RouteAssignmentData data = mock(RouteAssignmentData.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(assignmentRepository.save(any(RouteAssignment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(dataMapper.toRouteAssignmentData(any(RouteAssignment.class))).thenReturn(Mono.just(data));

        UpdateRouteAssignmentCommand cmd = new UpdateRouteAssignmentCommand(
                existing.getId().getValue(), null, null, null, null,
                "new reason", null, null);

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectNext(data)
                .verifyComplete();
    }

    @Test
    void errorsWhenAssignmentNotFound() {
        String id = RouteAssignmentId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findById(any(RouteAssignmentId.class))).thenReturn(Mono.empty());

        UpdateRouteAssignmentCommand cmd = new UpdateRouteAssignmentCommand(
                id, null, null, null, null, null, null, null);

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(RouteAssignmentNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
