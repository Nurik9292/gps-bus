package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PlacementTargetView(
        @JsonProperty("target_type") String targetType,
        @JsonProperty("target_id")   String targetId
) {

    public static PlacementTargetView fromDomain(PlacementTarget target) {
        return new PlacementTargetView(target.getTargetType().name(), target.getTargetId());
    }
}
