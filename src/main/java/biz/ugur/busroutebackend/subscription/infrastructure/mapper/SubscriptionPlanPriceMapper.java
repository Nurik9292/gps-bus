package biz.ugur.busroutebackend.subscription.infrastructure.mapper;

import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.model.SubscriptionPlanPrice;
import biz.ugur.busroutebackend.subscription.infrastructure.persistence.entity.SubscriptionPlanPriceEntity;

public final class SubscriptionPlanPriceMapper {

    private SubscriptionPlanPriceMapper() {}

    public static SubscriptionPlanPriceEntity toEntity(SubscriptionPlanPrice price) {
        return SubscriptionPlanPriceEntity.builder()
                .id(price.getPeriod().name())
                .amountMinor(price.getAmountMinor())
                .currency(price.getCurrency())
                .updatedBy(price.getUpdatedBy())
                .createdAt(price.getCreatedAt())
                .updatedAt(price.getUpdatedAt())
                .version(price.getVersion())
                .build();
    }

    public static SubscriptionPlanPrice toDomain(SubscriptionPlanPriceEntity entity) {
        return SubscriptionPlanPrice.restore(
                SubscriptionPeriod.valueOf(entity.getId()),
                entity.getAmountMinor(),
                entity.getCurrency(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
