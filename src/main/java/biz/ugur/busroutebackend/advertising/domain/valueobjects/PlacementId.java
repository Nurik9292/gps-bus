package biz.ugur.busroutebackend.advertising.domain.valueobjects;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class PlacementId extends ValueObject {

    private final String value;

    private PlacementId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new AdvertisingValidationException("placementId", "must not be blank");
        }
        this.value = value.trim();
    }

    public static PlacementId generate() {
        return new PlacementId(UUID.randomUUID().toString());
    }

    public static PlacementId of(String value) {
        return new PlacementId(value);
    }
}
