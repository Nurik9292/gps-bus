package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record BannerResponse(
        String id,
        String title,
        @JsonProperty("type") String type,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("target_url") String targetUrl,
        String content,
        @JsonProperty("is_active") boolean isActive,
        @JsonProperty("display_order") int displayOrder,
        @JsonProperty("start_date") LocalDateTime startDate,
        @JsonProperty("end_date") LocalDateTime endDate
) {}
