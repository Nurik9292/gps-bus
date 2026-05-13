package biz.ugur.busroutebackend.advertising.domain.enums;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;

import java.util.Arrays;

public enum PlacementKind {
    COMMERCIAL,
    EDITORIAL;

    public static PlacementKind from(String raw) {
        if (raw == null || raw.isBlank()) {
            return COMMERCIAL;
        }
        return Arrays.stream(values())
                .filter(k -> k.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseThrow(() -> new AdvertisingValidationException(
                        "kind", "unknown placement kind: " + raw));
    }
}
