package biz.ugur.busroutebackend.subscription.infrastructure.persistence.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
public class SubscriptionPlanPriceEntity {

    private final String id;
    private final long amountMinor;
    private final String currency;
    private final String updatedBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final Long version;
}
