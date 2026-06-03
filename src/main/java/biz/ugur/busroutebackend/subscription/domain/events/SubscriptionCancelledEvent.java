package biz.ugur.busroutebackend.subscription.domain.events;

import lombok.Getter;

@Getter
public class SubscriptionCancelledEvent extends SubscriptionDomainEvent {

    private final String clientId;
    private final String reason;

    public SubscriptionCancelledEvent(String subscriptionId, String clientId, String reason) {
        super(subscriptionId);
        this.clientId = clientId;
        this.reason = reason;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
