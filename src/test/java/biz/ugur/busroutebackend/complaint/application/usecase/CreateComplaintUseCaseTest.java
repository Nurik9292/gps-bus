package biz.ugur.busroutebackend.complaint.application.usecase;

import biz.ugur.busroutebackend.complaint.application.dto.CreateComplaintInput;
import biz.ugur.busroutebackend.complaint.domain.model.Complaint;
import biz.ugur.busroutebackend.complaint.domain.model.ComplaintType;
import biz.ugur.busroutebackend.complaint.domain.repository.ComplaintRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CreateComplaintUseCaseTest {

    @InjectMocks
    private CreateComplaintUseCase useCase;

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void createsComplaintAndSendsEmailNotification() {
        CreateComplaintInput input = new CreateComplaintInput(
                ComplaintType.BUS, "Missing stop", "Stop 7 removed", "client-1");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(complaintRepository.save(any(Complaint.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(emailNotificationService.sendComplaintNotification(anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(result -> {
                    assertNotNull(result.id());
                    assertEquals("BUS", result.type());
                    assertEquals("Missing stop", result.title());
                    assertEquals("Stop 7 removed", result.description());
                    assertEquals("NEW", result.status());
                    assertEquals("client-1", result.clientId());
                })
                .verifyComplete();

        verify(complaintRepository).save(any(Complaint.class));
        verify(emailNotificationService).sendComplaintNotification("Missing stop", "BUS", "Stop 7 removed");
    }

    @Test
    void rejectsInvalidInputBeforePersistence() {
        CreateComplaintInput bad = new CreateComplaintInput(
                ComplaintType.ROUTE, "   ", "desc", "client-1");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        StepVerifier.create(useCase.execute(Mono.just(bad)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();

        verify(complaintRepository, never()).save(any());
        verify(emailNotificationService, never()).sendComplaintNotification(anyString(), anyString(), anyString());
    }

    @Test
    void exposesComplaintBoundContext() {
        assertEquals("complaint", useCase.getBoundContext());
    }
}
