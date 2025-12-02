package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.notification;

import biz.ugur.busroutebackend.notification.application.dto.CreateNotificationCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NotificationCreateRequest {
    @JsonProperty("title")
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    @JsonProperty("displayOrder")
    private Integer displayOrder = 0;

    @JsonProperty("content")
    String content;

    @JsonProperty("isActive")
    private Boolean isActive = true;

    public CreateNotificationCommand toCommand() {
        return CreateNotificationCommand.builder()
                .title(this.title)
                .displayOrder(this.displayOrder)
                .content(this.content)
                .isActive(this.isActive != null ? this.isActive : true)
                .build();
    }
}
