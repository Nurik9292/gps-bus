package biz.ugur.busroutebackend.subscription.domain.events;

import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import lombok.Getter;

@Getter
public class SubscriptionInitiatedEvent extends SubscriptionDomainEvent {

    private final String clientId;
    private final SubscriptionPeriod period;
    private final long amountMinor;
    private final String currency;

    public SubscriptionInitiatedEvent(String subscriptionId,
                                       String clientId,
                                       SubscriptionPeriod period,
                                       long amountMinor,
                                       String currency) {
        super(subscriptionId);
        this.clientId = clientId;
        this.period = period;
        this.amountMinor = amountMinor;
        this.currency = currency;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
