package biz.ugur.busroutebackend.transport.domain.valueobject;

import lombok.Getter;

@Getter
public enum GpsValidationResult {

    VALID("Position is valid"),

    NULL_POSITION("GPS position is null"),

    INVALID_DEVICE_ID("Device ID is null or blank"),

    NULL_COORDINATES("Coordinates are null"),

    OUT_OF_BOUNDS("Coordinates are outside Turkmenistan bounds");

    private final String description;

    GpsValidationResult(String description) {
        this.description = description;
    }

    public boolean isValid() {
        return this == VALID;
    }

    public boolean isOutOfBounds() {
        return this == OUT_OF_BOUNDS;
    }
}
