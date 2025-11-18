package biz.ugur.busroutebackend.banner.application.dto;

import biz.ugur.busroutebackend.banner.domain.model.Banner;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;


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
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime startDate,

        @JsonProperty("end_date")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime endDate,

        @JsonProperty("content")
        String content,

        @JsonProperty("reply_time")
        Integer replyTime,

        @JsonProperty("created_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,

        @JsonProperty("updated_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime updatedAt
) {


}
