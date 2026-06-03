package biz.ugur.busroutebackend.subscription.infrastructure.persistence.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
public class SubscriptionEntity {

    private final String id;
    private final String clientId;
    private final String period;
    private final String status;
    private final String paymentId;
    private final long amountMinor;
    private final String currency;
    private final LocalDateTime startedAt;
    private final LocalDateTime expiresAt;
    private final LocalDateTime cancelledAt;
    private final String cancellationReason;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final Long version;
}
