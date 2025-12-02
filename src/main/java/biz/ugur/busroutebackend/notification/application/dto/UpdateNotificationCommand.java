package biz.ugur.busroutebackend.notification.application.dto;

import lombok.Builder;

@Builder
public record UpdateNotificationCommand(
        String id,
        String title,
        Integer displayOrder,
        boolean isActive,
        String content
) {
}
