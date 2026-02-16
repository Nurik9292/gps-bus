package biz.ugur.busroutebackend.place.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class PlaceAliasId extends ValueObject {

    private final String value;

    public PlaceAliasId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("PlaceAlias ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static PlaceAliasId generate() {
        return new PlaceAliasId(UUID.randomUUID().toString());
    }

    public static PlaceAliasId of(String value) {
        return new PlaceAliasId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
