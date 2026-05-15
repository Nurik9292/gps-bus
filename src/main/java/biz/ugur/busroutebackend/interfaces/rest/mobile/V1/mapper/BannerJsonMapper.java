package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.mapper;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.dto.BannerResponse;
import biz.ugur.busroutebackend.shared.utility.BannerTypeTargetTypeMapper;

import java.util.List;

public final class BannerJsonMapper {

    private BannerJsonMapper() {}

    public static BannerResponse fromAdPlacement(AdPlacement p) {
        List<PlacementTarget> targets = p.getTargets();
        BannerType bannerType = (targets != null && !targets.isEmpty())
                ? BannerTypeTargetTypeMapper.fromTarget(targets.get(0).getTargetType())
                : null;
        String typeString = bannerType != null ? bannerType.getValue() : null;

        boolean linkType = p.getContentType() == ContentType.LINK;

        return new BannerResponse(
                p.getId().getValue(),
                p.getTitle(),
                typeString,
                p.getImageUrl(),
                linkType ? p.getTargetUrl() : null,
                linkType ? null : p.getContent(),
                p.getStatus() == PlacementStatus.ACTIVE,
                p.getDisplayOrder() != null ? p.getDisplayOrder() : 0,
                p.getWindow() != null ? p.getWindow().getStartsAt() : null,
                p.getWindow() != null ? p.getWindow().getEndsAt() : null
        );
    }
}
