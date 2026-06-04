package biz.ugur.busroutebackend.subscription.domain.events;

import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class SubscriptionActivatedEvent extends SubscriptionDomainEvent {

    private final String clientId;
    private final SubscriptionPeriod period;
    private final LocalDateTime startedAt;
    private final LocalDateTime expiresAt;

    public SubscriptionActivatedEvent(String subscriptionId,
                                       String clientId,
                                       SubscriptionPeriod period,
                                       LocalDateTime startedAt,
                                       LocalDateTime expiresAt) {
        super(subscriptionId);
        this.clientId = clientId;
        this.period = period;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
