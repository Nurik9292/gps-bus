package biz.ugur.busroutebackend.business.domain.enums;

import biz.ugur.busroutebackend.business.domain.exceptions.BusinessValidationException;

import java.util.Arrays;

public enum BusinessType {
    RETAIL,
    RESTAURANT,
    SERVICE,
    ENTERTAINMENT,
    EDUCATION,
    HEALTHCARE,
    TRANSPORT,
    OTHER;

    public static BusinessType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessValidationException("businessType", "must not be null");
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessValidationException(
                        "businessType",
                        "unknown business type: " + raw));
    }
}
