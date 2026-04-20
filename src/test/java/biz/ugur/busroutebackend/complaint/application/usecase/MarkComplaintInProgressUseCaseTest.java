package biz.ugur.busroutebackend.complaint.application.usecase;

import biz.ugur.busroutebackend.complaint.application.dto.ComplaintResult;
import biz.ugur.busroutebackend.complaint.application.dto.UpdateComplaintStatusInput;
import biz.ugur.busroutebackend.complaint.domain.exceptions.ComplaintNotFoundException;
import biz.ugur.busroutebackend.complaint.domain.model.Complaint;
import biz.ugur.busroutebackend.complaint.domain.model.ComplaintType;
import biz.ugur.busroutebackend.complaint.domain.repository.ComplaintRepository;
import biz.ugur.busroutebackend.complaint.domain.valueobjects.ComplaintId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.BeforeEach;
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
class MarkComplaintInProgressUseCaseTest {

    private Complaint original;
    private UpdateComplaintStatusInput input;

    @InjectMocks
    private MarkComplaintInProgressUseCase useCase;

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        original = Complaint.create(ComplaintType.BUS, "title", "description", "client-1");
        input = new UpdateComplaintStatusInput(original.getId().getValue(), null);
    }

    @Test
    void marksComplaintAsInProgressSuccessfully() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(complaintRepository.findById(original.getId())).thenReturn(Mono.just(original));
        when(complaintRepository.save(any(Complaint.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(result -> {
                    assertEquals(original.getId().getValue(), result.id());
                    assertEquals("IN_PROGRESS", result.status());
                    assertEquals("title", result.title());
                })
                .verifyComplete();

        verify(complaintRepository).findById(original.getId());
        verify(complaintRepository).save(any(Complaint.class));
    }

    @Test
    void errorsWhenComplaintNotFound() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(complaintRepository.findById(any(ComplaintId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .expectErrorSatisfies(err -> {
                    assertInstanceOf(ComplaintNotFoundException.class, err);
                    assertEquals("Complaint not found with ID: " + original.getId().getValue(), err.getMessage());
                })
                .verify();

        verify(complaintRepository, never()).save(any());
    }

    @Test
    void exposesComplaintBoundContext() {
        assertEquals("complaint", useCase.getBoundContext());
    }
}
