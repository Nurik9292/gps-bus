package biz.ugur.busroutebackend.transport.domain.valueobject;

import lombok.Getter;

@Getter
public enum GpsProviderType {

    CHINA("CHINA", "China GPS API"),

    TUGDK("TUGDK", "TUGDK Government GPS API");

    private final String code;
    private final String description;

    GpsProviderType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static GpsProviderType defaultProvider() {
        return CHINA;
    }

    public static GpsProviderType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return defaultProvider();
        }

        String normalizedCode = code.trim().toUpperCase();

        for (GpsProviderType type : values()) {
            if (type.code.equals(normalizedCode)) {
                return type;
            }
        }

        throw new IllegalArgumentException(
                "Unknown GPS provider type: " + code + ". Valid values: CHINA, TUGDK"
        );
    }

    public static GpsProviderType fromCodeOrDefault(String code) {
        try {
            return fromCode(code);
        } catch (IllegalArgumentException e) {
            return defaultProvider();
        }
    }

    public boolean isDefault() {
        return this == defaultProvider();
    }

    @Override
    public String toString() {
        return code;
    }
}
