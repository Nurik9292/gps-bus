package biz.ugur.busroutebackend.subscription.infrastructure.mapper;

import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionStatus;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.valueobjects.SubscriptionId;
import biz.ugur.busroutebackend.subscription.infrastructure.persistence.entity.SubscriptionEntity;

public final class SubscriptionMapper {

    private SubscriptionMapper() {}

    public static SubscriptionEntity toEntity(Subscription subscription) {
        return SubscriptionEntity.builder()
                .id(subscription.getId().getValue())
                .clientId(subscription.getClientId())
                .period(subscription.getPeriod().name())
                .status(subscription.getStatus().name())
                .paymentId(subscription.getPaymentId())
                .amountMinor(subscription.getAmountMinor())
                .currency(subscription.getCurrency())
                .startedAt(subscription.getStartedAt())
                .expiresAt(subscription.getExpiresAt())
                .cancelledAt(subscription.getCancelledAt())
                .cancellationReason(subscription.getCancellationReason())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .version(subscription.getVersion())
                .build();
    }

    public static Subscription toDomain(SubscriptionEntity entity) {
        return Subscription.restore(
                SubscriptionId.of(entity.getId()),
                entity.getClientId(),
                SubscriptionPeriod.valueOf(entity.getPeriod()),
                SubscriptionStatus.valueOf(entity.getStatus()),
                entity.getPaymentId(),
                entity.getAmountMinor(),
                entity.getCurrency(),
                entity.getStartedAt(),
                entity.getExpiresAt(),
                entity.getCancelledAt(),
                entity.getCancellationReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
