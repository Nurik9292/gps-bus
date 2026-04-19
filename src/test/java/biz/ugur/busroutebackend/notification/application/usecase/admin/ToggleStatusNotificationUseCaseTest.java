package biz.ugur.busroutebackend.notification.application.usecase.admin;

import biz.ugur.busroutebackend.notification.application.dto.NotificationResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class ToggleStatusNotificationUseCaseTest {

    @InjectMocks
    private ToggleStatusNotificationUseCase useCase;

    @Mock
    private AdminNotificationRepository notificationRepository;

    @Mock
    private NotificationResponseMapper notificationResponseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void activatesPreviouslyDeactivatedNotification() {
        Notification off = Notification.create(NotificationTitle.of("t"), 0, "c", true).deactivate();
        ToggleStatusNotificationUseCase.Request req =
                new ToggleStatusNotificationUseCase.Request(off.getId().getValue(), true);

        NotificationResponse response = buildResponse(off.getId().getValue(), true);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findById(off.getId())).thenReturn(Mono.just(off));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(notificationResponseMapper.toResponse(any(Notification.class))).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(Mono.just(req)))
                .assertNext(r -> assertTrue(r.isActive()))
                .verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertTrue(captor.getValue().getIsActive());
    }

    @Test
    void deactivatesWhenActiveFlagIsFalseOrNull() {
        Notification on = Notification.create(NotificationTitle.of("t"), 0, "c", true);
        ToggleStatusNotificationUseCase.Request req =
                new ToggleStatusNotificationUseCase.Request(on.getId().getValue(), null);

        NotificationResponse response = buildResponse(on.getId().getValue(), false);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findById(on.getId())).thenReturn(Mono.just(on));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(notificationResponseMapper.toResponse(any(Notification.class))).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(Mono.just(req)))
                .assertNext(r -> assertFalse(r.isActive()))
                .verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertFalse(captor.getValue().getIsActive());
    }

    @Test
    void errorsWhenNotificationNotFound() {
        String id = NotificationId.generate().getValue();
        ToggleStatusNotificationUseCase.Request req =
                new ToggleStatusNotificationUseCase.Request(id, true);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findById(any(NotificationId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(req)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }

    private NotificationResponse buildResponse(String id, boolean active) {
        return new NotificationResponse(
                id, "t", "c", active, 0,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }
}
