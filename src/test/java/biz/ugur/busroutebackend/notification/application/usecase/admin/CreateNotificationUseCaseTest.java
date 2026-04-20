package biz.ugur.busroutebackend.notification.application.usecase.admin;

import biz.ugur.busroutebackend.notification.application.dto.CreateNotificationCommand;
import biz.ugur.busroutebackend.notification.application.dto.NotificationResponse;
import biz.ugur.busroutebackend.notification.application.factory.NotificationFactory;
import biz.ugur.busroutebackend.notification.application.mapper.NotificationResponseMapper;
import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.notification.domain.repository.AdminNotificationRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CreateNotificationUseCaseTest {

    @InjectMocks
    private CreateNotificationUseCase useCase;

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
    void createsNotificationAndReturnsResponse() {
        CreateNotificationCommand cmd = CreateNotificationCommand.builder()
                .title("Title")
                .displayOrder(0)
                .content("body")
                .isActive(true)
                .build();

        Notification notification = Notification.create(
                NotificationTitle.of("Title"), 0, "body", true);
        NotificationResponse response = new NotificationResponse(
                notification.getId().getValue(), "Title", "body", true, 0,
                LocalDateTime.now(), LocalDateTime.now()
        );

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationFactory.create(cmd)).thenReturn(Mono.just(notification));
        when(notificationRepository.save(notification)).thenReturn(Mono.just(notification));
        when(notificationResponseMapper.toResponse(notification)).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(r -> assertEquals("Title", r.title()))
                .verifyComplete();

        verify(notificationRepository).save(notification);
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
