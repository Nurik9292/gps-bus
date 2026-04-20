package biz.ugur.busroutebackend.complaint.application.usecase;

import biz.ugur.busroutebackend.complaint.application.dto.GetAllComplaintsInput;
import biz.ugur.busroutebackend.complaint.domain.model.Complaint;
import biz.ugur.busroutebackend.complaint.domain.model.ComplaintStatus;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAllComplaintsUseCaseTest {

    @InjectMocks
    private GetAllComplaintsUseCase useCase;

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsFilteredComplaints() {
        Complaint complaint = Complaint.create(
                ComplaintType.BUS, "title", "description", "client-1");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(complaintRepository.findByFilters(eq(ComplaintStatus.NEW), eq(ComplaintType.BUS), any(Pageable.class)))
                .thenReturn(Flux.just(complaint));
        when(complaintRepository.countByFilters(ComplaintStatus.NEW, ComplaintType.BUS))
                .thenReturn(Mono.just(1L));

        GetAllComplaintsInput input = GetAllComplaintsInput.fromParams(
                1, 10, "createdAt", "desc", "NEW", "BUS");

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(list -> assertEquals(1, list.getComplaints().size()))
                .verifyComplete();
    }

    @Test
    void returnsAllWhenNoFilter() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(complaintRepository.findByFilters(any(), any(), any(Pageable.class)))
                .thenReturn(Flux.empty());
        when(complaintRepository.countByFilters(any(), any())).thenReturn(Mono.just(0L));

        GetAllComplaintsInput input = GetAllComplaintsInput.fromParams(
                1, 10, "createdAt", "desc", null, null);

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(list -> assertEquals(0, list.getComplaints().size()))
                .verifyComplete();
    }

    @Test
    void exposesComplaintBoundContext() {
        assertEquals("complaint", useCase.getBoundContext());
    }
}
