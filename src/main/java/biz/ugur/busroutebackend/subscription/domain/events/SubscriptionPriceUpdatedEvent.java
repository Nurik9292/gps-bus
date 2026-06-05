package biz.ugur.busroutebackend.subscription.domain.events;

import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
public class SubscriptionPriceUpdatedEvent extends SubscriptionDomainEvent {

    private final Map<String, Object> changes;

    public SubscriptionPriceUpdatedEvent(String period, Map<String, Object> changes) {
        super(period);
        this.changes = Collections.unmodifiableMap(new HashMap<>(changes));
    }

    @Override protected int getCurrentVersion() { return 1; }
}
