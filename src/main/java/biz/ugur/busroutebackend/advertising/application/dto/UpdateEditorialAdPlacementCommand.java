package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateEditorialAdPlacementCommand(
        @JsonProperty("placement_id")    String placementId,
        @JsonProperty("title")           String title,
        @JsonProperty("content")         String content,
        @JsonProperty("image_url")       String imageUrl,
        @JsonProperty("target_url")      String targetUrl,
        @JsonProperty("cta_text")        String ctaText,
        @JsonProperty("starts_at")       LocalDateTime startsAt,
        @JsonProperty("ends_at")         LocalDateTime endsAt,
        @JsonProperty("targets")         List<PlacementTargetSpec> targets,
        @JsonProperty("display_order")   Integer displayOrder,
        @JsonProperty("content_type")    ContentType contentType
) {
    public void validateContentConsistency() {
        if (contentType == null) {
            throw new IllegalArgumentException("contentType required");
        }
        if (contentType == ContentType.CONTENT && (content == null || content.isBlank())) {
            throw new IllegalArgumentException("content required for contentType=CONTENT");
        }
        if (contentType == ContentType.LINK && (targetUrl == null || targetUrl.isBlank())) {
            throw new IllegalArgumentException("targetUrl required for contentType=LINK");
        }
        if (contentType == ContentType.LINK && content != null && !content.isBlank()) {
            throw new IllegalArgumentException("content not allowed for contentType=LINK");
        }
    }
}
