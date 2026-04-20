package biz.ugur.busroutebackend.notification.application.usecase.admin;

import biz.ugur.busroutebackend.notification.application.dto.NotificationResponse;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAllNotificationsUseCaseTest {

    @InjectMocks
    private GetAllNotificationsUseCase useCase;

    @Mock
    private AdminNotificationRepository notificationRepository;

    @Mock
    private NotificationResponseMapper notificationResponseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsAllNotificationsWhenActiveOnlyFalse() {
        Notification n = Notification.create(NotificationTitle.of("t"), 0, "c", true);
        NotificationResponse response = new NotificationResponse(
                n.getId().getValue(), "t", "c", true, 0,
                LocalDateTime.now(), LocalDateTime.now());

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findAll()).thenReturn(Flux.just(n));
        when(notificationResponseMapper.toResponse(n)).thenReturn(Mono.just(response));
        when(notificationRepository.countActiveNotifications()).thenReturn(Mono.just(1L));

        StepVerifier.create(useCase.execute(Mono.just(false)))
                .assertNext(list -> {
                    assertEquals(1, list.getNotifications().size());
                    assertEquals(1L, list.getActiveCount());
                })
                .verifyComplete();
    }

    @Test
    void returnsActiveWhenActiveOnlyTrue() {
        Notification n = Notification.create(NotificationTitle.of("t"), 0, "c", true);
        NotificationResponse response = new NotificationResponse(
                n.getId().getValue(), "t", "c", true, 0,
                LocalDateTime.now(), LocalDateTime.now());

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findActiveNotifications()).thenReturn(Flux.just(n));
        when(notificationResponseMapper.toResponse(n)).thenReturn(Mono.just(response));
        when(notificationRepository.countActiveNotifications()).thenReturn(Mono.just(1L));

        StepVerifier.create(useCase.execute(Mono.just(true)))
                .assertNext(list -> assertEquals(1, list.getNotifications().size()))
                .verifyComplete();
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
