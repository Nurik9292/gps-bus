package biz.ugur.busroutebackend.notification.application.factory;

import biz.ugur.busroutebackend.notification.application.dto.CreateNotificationCommand;
import biz.ugur.busroutebackend.notification.application.dto.UpdateNotificationCommand;
import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationTitle;
import biz.ugur.busroutebackend.shared.application.compressor.DataCompressor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
public class NotificationFactory {

    private final DataCompressor dataCompressor;

    public NotificationFactory(DataCompressor dataCompressor) {
        this.dataCompressor = dataCompressor;
    }

    public Mono<Notification> create(CreateNotificationCommand command) {
        Mono<String> compressedContent = command.content() != null
                ? dataCompressor.compressAndEncode(command.content())
                : Mono.empty();

        return compressedContent
                .defaultIfEmpty("")
                .map(content ->
                        Notification.create(
                                NotificationTitle.of(command.title()),
                                command.displayOrder(),
                                content,
                                command.isActive()
                        )
                );
    }

    public Mono<Notification> update(Notification notification, UpdateNotificationCommand command) {
        Mono<String> compressedContent = command.content() != null
                ? dataCompressor.compressAndEncode(command.content())
                : Mono.justOrEmpty(notification.getContent());

        return compressedContent
                .defaultIfEmpty(notification.getContent() != null ? notification.getContent() : "")
                .map(content -> {
                    Notification updatedNotification = notification.updateNotification(
                            NotificationTitle.of(command.title()),
                            command.displayOrder(),
                            content
                    );

                    return command.isActive()
                            ? updatedNotification.activate()
                            : updatedNotification.deactivate();
                });
    }
}
