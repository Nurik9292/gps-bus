package biz.ugur.busroutebackend.notification.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;


public record NotificationResponse(
        @JsonProperty("id")
        String id,

        @JsonProperty("title")
        String title,

        @JsonProperty("content")
        String content,

        @JsonProperty("is_active")
        Boolean isActive,

        @JsonProperty("display_order")
        Integer displayOrder,

        @JsonProperty("created_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,

        @JsonProperty("updated_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime updatedAt
) {


}
