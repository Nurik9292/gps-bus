package biz.ugur.busroutebackend.advertising.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record CreateAdPlacementCommand(
        @JsonProperty("business_id")       String businessId,
        @JsonProperty("tariff_id")         String tariffId,
        @JsonProperty("placement_type")    String placementType,
        @JsonProperty("kind")              String kind,
        @JsonProperty("title")             String title,
        @JsonProperty("content")           String content,
        @JsonProperty("image_url")         String imageUrl,
        @JsonProperty("target_url")        String targetUrl,
        @JsonProperty("cta_text")          String ctaText,
        @JsonProperty("starts_at")         LocalDateTime startsAt,
        @JsonProperty("ends_at")           LocalDateTime endsAt,
        @JsonProperty("display_contexts")  List<String> displayContexts,
        @JsonProperty("targets")           List<PlacementTargetSpec> targets,
        @JsonProperty("display_order")     Integer displayOrder
) {}
