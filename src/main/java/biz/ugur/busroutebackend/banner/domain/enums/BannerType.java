package biz.ugur.busroutebackend.banner.domain.enums;


import lombok.Getter;
import lombok.ToString;

import java.util.EnumSet;

@Getter
@ToString
public enum BannerType {

    MAIN("main"),
    STOPS("stops"),
    ROUTES("routes"),
    PLACES("places"),
    POPUP("popup");

    private final String value;


    BannerType(String value) {
        this.value = value;
    }

    public static BannerType fromValue(String value) {
        for (BannerType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown BannerType: " + value);
    }
}
