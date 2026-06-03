package biz.ugur.busroutebackend.subscription.domain.events;

import lombok.Getter;

@Getter
public class SubscriptionExpiredEvent extends SubscriptionDomainEvent {

    private final String clientId;

    public SubscriptionExpiredEvent(String subscriptionId, String clientId) {
        super(subscriptionId);
        this.clientId = clientId;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
