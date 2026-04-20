package biz.ugur.busroutebackend.notification.application.usecase.admin;

import biz.ugur.busroutebackend.notification.application.dto.NotificationResponse;
import biz.ugur.busroutebackend.notification.application.dto.UpdateNotificationCommand;
import biz.ugur.busroutebackend.notification.application.factory.NotificationFactory;
import biz.ugur.busroutebackend.notification.application.mapper.NotificationResponseMapper;
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
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class UpdateNotificationUseCaseTest {

    @InjectMocks
    private UpdateNotificationUseCase useCase;

    @Mock
    private AdminNotificationRepository notificationRepository;

    @Mock
    private NotificationFactory notificationFactory;

    @Mock
    private NotificationResponseMapper notificationResponseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void updatesExistingNotification() {
        Notification existing = Notification.create(
                NotificationTitle.of("Old"), 0, "old content", true);

        UpdateNotificationCommand cmd = UpdateNotificationCommand.builder()
                .id(existing.getId().getValue())
                .title("New")
                .displayOrder(1)
                .content("new content")
                .isActive(true)
                .build();

        Notification updated = existing.updateNotification(
                NotificationTitle.of("New"), 1, "new content");

        NotificationResponse response = new NotificationResponse(
                existing.getId().getValue(), "New", "new content",
                true, 1, LocalDateTime.now(), LocalDateTime.now());

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(notificationFactory.update(existing, cmd)).thenReturn(Mono.just(updated));
        when(notificationRepository.save(updated)).thenReturn(Mono.just(updated));
        when(notificationResponseMapper.toResponse(updated)).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(r -> assertEquals("New", r.title()))
                .verifyComplete();
    }

    @Test
    void errorsWhenNotificationNotFound() {
        UpdateNotificationCommand cmd = UpdateNotificationCommand.builder()
                .id(NotificationId.generate().getValue())
                .title("X").displayOrder(0).content("").isActive(false)
                .build();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findById(any(NotificationId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
