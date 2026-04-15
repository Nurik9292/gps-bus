package biz.ugur.busroutebackend.advertising.infrastructure.mapper;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TariffPeriod;
import biz.ugur.busroutebackend.advertising.domain.model.AdTariff;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.Price;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffName;
import biz.ugur.busroutebackend.advertising.infrastructure.persistence.entity.AdTariffEntity;

public final class AdTariffMapper {

    private AdTariffMapper() {}

    public static AdTariff toDomain(AdTariffEntity entity) {
        return AdTariff.restore(
                TariffId.of(entity.getId()),
                TariffName.of(entity.getName()),
                entity.getDescription(),
                PlacementType.valueOf(entity.getPlacementType()),
                TariffPeriod.valueOf(entity.getPeriod()),
                Price.ofMinor(entity.getPriceAmount(), entity.getCurrency()),
                entity.getMaxImpressions(),
                entity.getMaxClicks(),
                entity.getDailyImpressionCap(),
                entity.getIsActive(),
                entity.getDisplayOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public static AdTariffEntity toEntity(AdTariff tariff) {
        return AdTariffEntity.builder()
                .id(tariff.getId().getValue())
                .name(tariff.getName().getValue())
                .description(tariff.getDescription())
                .placementType(tariff.getPlacementType().name())
                .period(tariff.getPeriod().name())
                .priceAmount(tariff.getPrice().getAmountMinor())
                .currency(tariff.getPrice().getCurrency())
                .maxImpressions(tariff.getMaxImpressions())
                .maxClicks(tariff.getMaxClicks())
                .dailyImpressionCap(tariff.getDailyImpressionCap())
                .isActive(tariff.getIsActive())
                .displayOrder(tariff.getDisplayOrder())
                .createdAt(tariff.getCreatedAt())
                .updatedAt(tariff.getUpdatedAt())
                .version(tariff.getVersion())
                .build();
    }
}
