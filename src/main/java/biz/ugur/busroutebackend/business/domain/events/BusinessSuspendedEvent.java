package biz.ugur.busroutebackend.business.domain.events;

import lombok.Getter;

@Getter
public class BusinessSuspendedEvent extends BusinessDomainEvent {

    private static final int VERSION = 1;

    private final String reason;

    public BusinessSuspendedEvent(String businessId, String reason) {
        super(businessId);
        this.reason = reason;
    }

    @Override
    protected int getCurrentVersion() {
        return VERSION;
    }
}
