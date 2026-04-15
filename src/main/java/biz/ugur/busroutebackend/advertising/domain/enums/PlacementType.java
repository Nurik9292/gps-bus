package biz.ugur.busroutebackend.advertising.domain.enums;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;

import java.util.Arrays;

public enum PlacementType {
    BANNER,
    PUSH,
    POPUP;

    public static PlacementType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AdvertisingValidationException("placementType", "must not be null");
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseThrow(() -> new AdvertisingValidationException(
                        "placementType", "unknown placement type: " + raw));
    }
}
