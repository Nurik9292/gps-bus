package biz.ugur.busroutebackend.advertising.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlacementTargetSpec(
        @JsonProperty("target_type") String targetType,
        @JsonProperty("target_id")   String targetId
) {}
