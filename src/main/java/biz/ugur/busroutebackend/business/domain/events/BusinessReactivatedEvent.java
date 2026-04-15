package biz.ugur.busroutebackend.business.domain.events;

public class BusinessReactivatedEvent extends BusinessDomainEvent {

    private static final int VERSION = 1;

    public BusinessReactivatedEvent(String businessId) {
        super(businessId);
    }

    @Override
    protected int getCurrentVersion() {
        return VERSION;
    }
}
