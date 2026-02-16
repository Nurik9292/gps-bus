package biz.ugur.busroutebackend.place.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class StreetAliasId extends ValueObject {

    private final String value;

    public StreetAliasId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("StreetAlias ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static StreetAliasId generate() {
        return new StreetAliasId(UUID.randomUUID().toString());
    }

    public static StreetAliasId of(String value) {
        return new StreetAliasId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
