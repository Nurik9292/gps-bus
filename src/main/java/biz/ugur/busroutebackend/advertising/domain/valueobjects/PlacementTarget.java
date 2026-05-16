package biz.ugur.busroutebackend.advertising.domain.valueobjects;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class PlacementTarget extends ValueObject {

    private final TargetType targetType;
    private final String targetId;

    private PlacementTarget(TargetType targetType, String targetId) {
        if (targetType == null) {
            throw new AdvertisingValidationException("targetType", "must not be null");
        }
        String trimmedId = targetId == null ? null : targetId.trim().isEmpty() ? null : targetId.trim();
        if (targetType.requiresTargetId() && trimmedId == null) {
            throw new AdvertisingValidationException(
                    "targetId", "must not be blank for targetType " + targetType);
        }
        if (targetType.forbidsTargetId() && trimmedId != null) {
            throw new AdvertisingValidationException(
                    "targetId", "must be null for targetType " + targetType);
        }
        this.targetType = targetType;
        this.targetId = trimmedId;
    }

    public static PlacementTarget general(TargetType targetType) {
        return new PlacementTarget(targetType, null);
    }

    public static PlacementTarget specific(TargetType targetType, String targetId) {
        return new PlacementTarget(targetType, targetId);
    }

    public static PlacementTarget of(TargetType targetType, String targetId) {
        return new PlacementTarget(targetType, targetId);
    }
}
