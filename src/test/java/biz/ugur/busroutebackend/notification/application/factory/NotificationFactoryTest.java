package biz.ugur.busroutebackend.notification.application.factory;

import biz.ugur.busroutebackend.notification.application.dto.CreateNotificationCommand;
import biz.ugur.busroutebackend.notification.application.dto.UpdateNotificationCommand;
import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationTitle;
import biz.ugur.busroutebackend.shared.application.compressor.DataCompressor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class NotificationFactoryTest {

    @InjectMocks
    private NotificationFactory factory;

    @Mock
    private DataCompressor dataCompressor;

    @Test
    void createCompressesContentAndReturnsActiveNotification() {
        CreateNotificationCommand cmd = CreateNotificationCommand.builder()
                .title("Route update")
                .displayOrder(1)
                .content("Detailed info")
                .isActive(true)
                .build();

        when(dataCompressor.compressAndEncode("Detailed info")).thenReturn(Mono.just("COMPRESSED"));

        StepVerifier.create(factory.create(cmd))
                .assertNext(notification -> {
                    assertEquals("Route update", notification.getTitle().getValue());
                    assertEquals("COMPRESSED", notification.getContent());
                    assertTrue(notification.getIsActive());
                })
                .verifyComplete();
    }

    @Test
    void createUsesEmptyContentWhenContentIsNull() {
        CreateNotificationCommand cmd = CreateNotificationCommand.builder()
                .title("Route update")
                .displayOrder(0)
                .content(null)
                .isActive(false)
                .build();

        StepVerifier.create(factory.create(cmd))
                .assertNext(notification -> {
                    assertEquals("", notification.getContent());
                    assertFalse(notification.getIsActive());
                })
                .verifyComplete();
    }

    @Test
    void updateCompressesNewContentAndActivates() {
        Notification existing = Notification.create(
                NotificationTitle.of("Old"), 0, "old content", false);

        UpdateNotificationCommand cmd = UpdateNotificationCommand.builder()
                .id(existing.getId().getValue())
                .title("New")
                .displayOrder(5)
                .content("new content")
                .isActive(true)
                .build();

        when(dataCompressor.compressAndEncode("new content")).thenReturn(Mono.just("NEW_COMPRESSED"));

        StepVerifier.create(factory.update(existing, cmd))
                .assertNext(notification -> {
                    assertEquals("New", notification.getTitle().getValue());
                    assertEquals("NEW_COMPRESSED", notification.getContent());
                    assertTrue(notification.getIsActive());
                })
                .verifyComplete();
    }

    @Test
    void updateDeactivatesWhenIsActiveFalse() {
        Notification existing = Notification.create(
                NotificationTitle.of("Title"), 0, "content", true);

        UpdateNotificationCommand cmd = UpdateNotificationCommand.builder()
                .id(existing.getId().getValue())
                .title("Title")
                .displayOrder(0)
                .content(null)
                .isActive(false)
                .build();

        StepVerifier.create(factory.update(existing, cmd))
                .assertNext(notification -> assertFalse(notification.getIsActive()))
                .verifyComplete();
    }
}
