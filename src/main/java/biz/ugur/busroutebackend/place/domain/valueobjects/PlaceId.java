package biz.ugur.busroutebackend.place.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class PlaceId extends ValueObject {

    private final String value;

    public PlaceId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Place ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static PlaceId generate() {
        return new PlaceId(UUID.randomUUID().toString());
    }

    public static PlaceId of(String value) {
        return new PlaceId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
