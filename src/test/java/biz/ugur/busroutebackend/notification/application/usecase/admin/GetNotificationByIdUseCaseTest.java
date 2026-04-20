package biz.ugur.busroutebackend.notification.application.usecase.admin;

import biz.ugur.busroutebackend.notification.application.dto.NotificationResponse;
import biz.ugur.busroutebackend.notification.application.mapper.NotificationResponseMapper;
import biz.ugur.busroutebackend.notification.domain.exceptions.NotificationNotFoundException;
import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.notification.domain.repository.AdminNotificationRepository;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationId;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationTitle;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetNotificationByIdUseCaseTest {

    @InjectMocks
    private GetNotificationByIdUseCase useCase;

    @Mock
    private AdminNotificationRepository notificationRepository;

    @Mock
    private NotificationResponseMapper notificationResponseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsMappedResponseWhenFound() {
        Notification existing = Notification.create(NotificationTitle.of("hello"), 1, "body", true);
        NotificationResponse response = new NotificationResponse(
                existing.getId().getValue(), "hello", "body", true, 1,
                LocalDateTime.now(), LocalDateTime.now()
        );

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(notificationResponseMapper.toResponse(existing)).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(Mono.just(existing.getId().getValue())))
                .assertNext(r -> {
                    assertEquals(existing.getId().getValue(), r.id());
                    assertEquals("hello", r.title());
                })
                .verifyComplete();

        verify(notificationResponseMapper).toResponse(existing);
    }

    @Test
    void errorsWithDomainExceptionWhenNotFound() {
        String id = NotificationId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findById(any(NotificationId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(id)))
                .expectErrorSatisfies(err -> assertInstanceOf(NotificationNotFoundException.class, err))
                .verify();

        verify(notificationResponseMapper, never()).toResponse(any());
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
