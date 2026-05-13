package biz.ugur.busroutebackend.advertising.domain.enums;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;

import java.util.Arrays;
import java.util.Set;

public enum TargetType {
    HOME,
    POPUP,
    ROUTES_LIST,
    STOPS_LIST,
    PLACES_LIST,
    ROUTE,
    STOP,
    PLACE;

    private static final Set<TargetType> SPECIFIC = Set.of(ROUTE, STOP, PLACE);

    public boolean requiresTargetId() {
        return SPECIFIC.contains(this);
    }

    public boolean forbidsTargetId() {
        return !requiresTargetId();
    }

    public static TargetType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AdvertisingValidationException("targetType", "must not be blank");
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseThrow(() -> new AdvertisingValidationException(
                        "targetType", "unknown target type: " + raw));
    }
}
