package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.request;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record RecordEventRequest(
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
