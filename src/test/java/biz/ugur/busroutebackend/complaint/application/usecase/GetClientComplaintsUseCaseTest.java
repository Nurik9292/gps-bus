package biz.ugur.busroutebackend.complaint.application.usecase;

import biz.ugur.busroutebackend.complaint.application.dto.GetClientComplaintsInput;
import biz.ugur.busroutebackend.complaint.domain.model.Complaint;
import biz.ugur.busroutebackend.complaint.domain.model.ComplaintType;
import biz.ugur.busroutebackend.complaint.domain.repository.ComplaintRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetClientComplaintsUseCaseTest {

    @InjectMocks
    private GetClientComplaintsUseCase useCase;

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsClientComplaintsWithPagination() {
        Complaint complaint = Complaint.create(
                ComplaintType.BUS, "title", "description", "client-1");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(complaintRepository.findByClientId(anyString(), any(Pageable.class)))
                .thenReturn(Flux.just(complaint));
        when(complaintRepository.countByClientId("client-1")).thenReturn(Mono.just(1L));

        GetClientComplaintsInput input = GetClientComplaintsInput.fromParams(
                "client-1", 1, 10, "createdAt", "desc");

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(list -> assertEquals(1, list.getComplaints().size()))
                .verifyComplete();
    }

    @Test
    void returnsEmptyListWhenClientHasNoComplaints() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(complaintRepository.findByClientId(anyString(), any(Pageable.class)))
                .thenReturn(Flux.empty());
        when(complaintRepository.countByClientId(anyString())).thenReturn(Mono.just(0L));

        GetClientComplaintsInput input = GetClientComplaintsInput.fromParams(
                "client-x", 1, 10, "createdAt", "desc");

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(list -> assertEquals(0, list.getComplaints().size()))
                .verifyComplete();
    }

    @Test
    void exposesComplaintBoundContext() {
        assertEquals("complaint", useCase.getBoundContext());
    }
}
