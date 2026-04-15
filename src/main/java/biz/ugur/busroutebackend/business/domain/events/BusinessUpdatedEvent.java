package biz.ugur.busroutebackend.business.domain.events;

import lombok.Getter;

import java.util.Map;

@Getter
public class BusinessUpdatedEvent extends BusinessDomainEvent {

    private static final int VERSION = 1;

    private final Map<String, Object> changes;

    public BusinessUpdatedEvent(String businessId, Map<String, Object> changes) {
        super(businessId);
        this.changes = Map.copyOf(changes);
    }

    @Override
    protected int getCurrentVersion() {
        return VERSION;
    }
}
