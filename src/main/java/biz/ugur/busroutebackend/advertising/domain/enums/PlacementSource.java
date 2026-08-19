package biz.ugur.busroutebackend.advertising.domain.enums;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;

import java.util.Arrays;

public enum PlacementSource {
    MANUAL,
    EXTERNAL;

    public static PlacementSource from(String raw) {
        if (raw == null || raw.isBlank()) {
            return MANUAL;
        }
        return Arrays.stream(values())
                .filter(source -> source.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseThrow(() -> new AdvertisingValidationException(
                        "source", "unknown placement source: " + raw));
    }
}
