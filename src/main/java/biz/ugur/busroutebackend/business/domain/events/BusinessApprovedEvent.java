package biz.ugur.busroutebackend.business.domain.events;

import lombok.Getter;

@Getter
public class BusinessApprovedEvent extends BusinessDomainEvent {

    private static final int VERSION = 1;

    private final String approvedByAdminId;

    public BusinessApprovedEvent(String businessId, String approvedByAdminId) {
        super(businessId);
        this.approvedByAdminId = approvedByAdminId;
    }

    @Override
    protected int getCurrentVersion() {
        return VERSION;
    }
}
