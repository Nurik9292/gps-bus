package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.request;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RecordDetailViewRequest(
        @JsonProperty("durationMs")
        @NotNull @Min(0) @Max(1_800_000) Integer durationMs,

        @JsonProperty("targetType") TargetType targetType,
        @JsonProperty("targetId")   UUID targetId
) {
    public void validateTargetConsistency() {
        if (targetType == null && targetId != null) {
            throw new IllegalArgumentException("targetId provided without targetType");
        }
        if (targetType != null && targetType.requiresTargetId() && targetId == null) {
            throw new IllegalArgumentException("targetType=" + targetType + " requires targetId");
        }
        if (targetType != null && !targetType.requiresTargetId() && targetId != null) {
            throw new IllegalArgumentException("targetType=" + targetType + " must not have targetId");
        }
    }
}
