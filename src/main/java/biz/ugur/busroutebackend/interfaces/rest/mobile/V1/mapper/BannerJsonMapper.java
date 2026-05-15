package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.mapper;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.shared.utility.BannerTypeTargetTypeMapper;

import java.util.List;

public final class BannerJsonMapper {

    private BannerJsonMapper() {}

    public static BannerResponse fromAdPlacement(AdPlacement p) {
        TargetType resolvedTargetType = firstTargetType(p.getTargets());
        return fromAdPlacement(p, resolvedTargetType);
    }

    public static BannerResponse fromAdPlacement(AdPlacement p, TargetType targetType) {
        BannerType bannerType = targetType != null
                ? BannerTypeTargetTypeMapper.fromTarget(targetType)
                : null;
        String typeString = bannerType != null ? bannerType.getValue() : null;

        boolean linkType = p.getContentType() == ContentType.LINK;

        return new BannerResponse(
                p.getId().getValue(),
                p.getTitle(),
                typeString,
                p.getImageUrl(),
                linkType ? p.getTargetUrl() : null,
                p.getStatus() == PlacementStatus.ACTIVE,
                p.getDisplayOrder() != null ? p.getDisplayOrder() : 0,
                p.getWindow() != null ? p.getWindow().getStartsAt() : null,
                p.getWindow() != null ? p.getWindow().getEndsAt() : null,
                linkType ? null : p.getContent(),
                null,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private static TargetType firstTargetType(List<PlacementTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return null;
        }
        return targets.get(0).getTargetType();
    }
}
