package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.notification;

import biz.ugur.busroutebackend.notification.application.dto.UpdateNotificationCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class NotificationUpdateRequest {

    @JsonProperty("title")
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    private Boolean isActive;

    private Integer displayOrder;

    @JsonProperty("content")
    private String content;

    public UpdateNotificationCommand toCommand(String id) {
        return UpdateNotificationCommand.builder()
                .id(id)
                .title(this.title)
                .isActive(this.isActive)
                .displayOrder(this.displayOrder)
                .content(this.content)
                .build();
    }
}
