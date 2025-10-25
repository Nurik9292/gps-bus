package biz.ugur.busroutebackend.banner.infrastructure.mapper;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerImage;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerTitle;
import biz.ugur.busroutebackend.banner.infrastructure.persistence.entity.BannerEntity;

public class BannerMapper {

    private BannerMapper() {}

    public static Banner toDomain(BannerEntity entity) {
        return Banner.restore(
                BannerId.of(entity.getId()),
                BannerTitle.of(entity.getTitle()),
                BannerType.fromValue(entity.getType()),
                BannerPeriod.between(entity.getStartDate(), entity.getEndDate()),
                BannerImage.of(entity.getImageUrl()),
                entity.getTargetUrl(),
                entity.getIsActive(),
                entity.getDisplayOrder(),
                entity.getContent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public static BannerEntity toEntity(Banner banner) {
        return BannerEntity.builder()
                .id(banner.getId().getValue())
                .title(banner.getTitle().getValue())
                .type(banner.getType().getValue())
                .imageUrl(banner.getImageUrl().getValue())
                .targetUrl(banner.getTargetUrl())
                .isActive(banner.getIsActive())
                .displayOrder(banner.getDisplayOrder())
                .startDate(banner.getPeriod().getStartTime())
                .endDate(banner.getPeriod().getEndTime())
                .content(banner.getContent())
                .createdAt(banner.getCreatedAt())
                .updatedAt(banner.getUpdatedAt())
                .version(banner.getVersion())
                .build();
    }
}
