package biz.ugur.busroutebackend.place.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class StreetId extends ValueObject {

    private final String value;

    public StreetId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Street ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static StreetId generate() {
        return new StreetId(UUID.randomUUID().toString());
    }

    public static StreetId of(String value) {
        return new StreetId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
