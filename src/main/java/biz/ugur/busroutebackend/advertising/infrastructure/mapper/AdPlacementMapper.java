package biz.ugur.busroutebackend.advertising.infrastructure.mapper;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementSource;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.advertising.infrastructure.persistence.entity.AdPlacementEntity;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;

import java.util.List;

public final class AdPlacementMapper {

    private AdPlacementMapper() {}

    public static AdPlacement toDomain(AdPlacementEntity e) {
        return toDomain(e, List.of());
    }

    public static AdPlacement toDomain(AdPlacementEntity e, List<PlacementTarget> targets) {
        ContentType ct = e.getContentType() != null
                ? ContentType.valueOf(e.getContentType())
                : ContentType.LINK;
        return AdPlacement.restore(
                PlacementSource.from(e.getSource()),
                e.getExternalServiceId(),
                e.getExternalRef(),
                PlacementId.of(e.getId()),
                e.getBusinessId() != null ? BusinessId.of(e.getBusinessId()) : null,
                e.getTariffId() != null ? TariffId.of(e.getTariffId()) : null,
                PlacementType.valueOf(e.getPlacementType()),
                e.getKind() != null ? PlacementKind.from(e.getKind()) : PlacementKind.COMMERCIAL,
                PlacementStatus.valueOf(e.getStatus()),
                e.getTitle(),
                e.getContent(),
                e.getImageUrl(),
                e.getTargetUrl(),
                e.getCtaText(),
                ct,
                PlacementWindow.of(e.getStartsAt(), e.getEndsAt()),
                targets,
                e.getDisplayOrder(),
                e.getRejectionReason(),
                e.getApprovedAt(),
                e.getApprovedByAdminId(),
                e.getRejectedAt(),
                e.getRejectedByAdminId(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion()
        );
    }

    public static AdPlacementEntity toEntity(AdPlacement p) {
        PlacementWindow window = p.getWindow();
        return AdPlacementEntity.builder()
                .id(p.getId().getValue())
                .businessId(p.getBusinessId() != null ? p.getBusinessId().getValue() : null)
                .tariffId(p.getTariffId() != null ? p.getTariffId().getValue() : null)
                .placementType(p.getPlacementType().name())
                .kind(p.getKind() != null ? p.getKind().name() : PlacementKind.COMMERCIAL.name())
                .status(p.getStatus().name())
                .title(p.getTitle())
                .content(p.getContent())
                .imageUrl(p.getImageUrl())
                .targetUrl(p.getTargetUrl())
                .ctaText(p.getCtaText())
                .contentType(p.getContentType() != null
                        ? p.getContentType().name()
                        : ContentType.LINK.name())
                .startsAt(window != null ? window.getStartsAt() : null)
                .endsAt(window != null ? window.getEndsAt() : null)
                .displayOrder(p.getDisplayOrder())
                .source(p.getSource() != null ? p.getSource().name() : PlacementSource.MANUAL.name())
                .externalServiceId(p.getExternalServiceId())
                .externalRef(p.getExternalRef())
                .rejectionReason(p.getRejectionReason())
                .approvedAt(p.getApprovedAt())
                .approvedByAdminId(p.getApprovedByAdminId())
                .rejectedAt(p.getRejectedAt())
                .rejectedByAdminId(p.getRejectedByAdminId())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .version(p.getVersion())
                .build();
    }
}
