package biz.ugur.busroutebackend.notification.application.mapper;

import biz.ugur.busroutebackend.notification.application.dto.NotificationResponse;
import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.shared.application.compressor.DataCompressor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class NotificationResponseMapper {

    private final DataCompressor dataCompressor;

    public NotificationResponseMapper(DataCompressor dataCompressor) {
        this.dataCompressor = dataCompressor;
    }

    public Mono<NotificationResponse> toResponse(Notification notification) {
        return dataCompressor.decodeAndDecompress(notification.getContent())
                .defaultIfEmpty("")
                .map(decompressedContent -> new NotificationResponse(
                        notification.getId().getValue(),
                        notification.getTitle().getValue(),
                        decompressedContent,
                        notification.getIsActive(),
                        notification.getDisplayOrder(),
                        notification.getCreatedAt(),
                        notification.getUpdatedAt()
                ));
    }
}
