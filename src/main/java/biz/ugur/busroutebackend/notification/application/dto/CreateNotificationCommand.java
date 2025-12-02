package biz.ugur.busroutebackend.notification.application.dto;

import lombok.Builder;

@Builder
public record CreateNotificationCommand(
        String title,
        Integer displayOrder,
        String content,
        Boolean isActive
) {

}
