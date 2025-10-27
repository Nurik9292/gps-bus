package biz.ugur.busroutebackend.banner.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Banner response DTO.
 * Uses Java record for immutability and concise syntax.
 */
public record BannerResponse(
        @JsonProperty("id")
        String id,

        @JsonProperty("title")
        String title,

        @JsonProperty("type")
        String type,

        @JsonProperty("image_url")
        String imageUrl,

        @JsonProperty("target_url")
        String targetUrl,

        @JsonProperty("is_active")
        Boolean isActive,

        @JsonProperty("display_order")
        Integer displayOrder,

        @JsonProperty("start_date")
        LocalDateTime startDate,

        @JsonProperty("end_date")
        LocalDateTime endDate,

        @JsonProperty("content")
        String content
) {
}
