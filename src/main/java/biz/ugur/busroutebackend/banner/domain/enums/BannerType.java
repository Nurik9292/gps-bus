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


    public boolean isSameAs(BannerType type) {
        return this == type;
    }

    public boolean isOneOf(EnumSet<BannerType> types) {
        return types != null && types.contains(this);
    }

    public static final EnumSet<BannerType> HOME_PAGE = EnumSet.of(MAIN, POPUP);
    public static final EnumSet<BannerType> TRANSPORT = EnumSet.of(STOPS, ROUTES);
}
