package biz.ugur.busroutebackend.shared.utility;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;

import java.util.Map;

public final class BannerTypeTargetTypeMapper {

    private static final Map<BannerType, TargetType> TO_TARGET = Map.of(
            BannerType.MAIN,   TargetType.HOME,
            BannerType.STOPS,  TargetType.STOPS_LIST,
            BannerType.ROUTES, TargetType.ROUTES_LIST,
            BannerType.PLACES, TargetType.PLACES_LIST,
            BannerType.POPUP,  TargetType.POPUP
    );

    private BannerTypeTargetTypeMapper() {}

    public static TargetType toTarget(BannerType bannerType) {
        TargetType t = TO_TARGET.get(bannerType);
        if (t == null) {
            throw new IllegalArgumentException("Unsupported BannerType: " + bannerType);
        }
        return t;
    }

    public static BannerType fromTarget(TargetType targetType) {
        return TO_TARGET.entrySet().stream()
                .filter(e -> e.getValue() == targetType)
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
