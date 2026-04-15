package biz.ugur.busroutebackend.business.domain.events;

import biz.ugur.busroutebackend.business.domain.enums.BusinessType;
import lombok.Getter;

@Getter
public class BusinessCreatedEvent extends BusinessDomainEvent {

    private static final int VERSION = 1;

    private final String name;
    private final BusinessType type;
    private final String contactPhone;

    public BusinessCreatedEvent(String businessId,
                                 String name,
                                 BusinessType type,
                                 String contactPhone) {
        super(businessId);
        this.name = name;
        this.type = type;
        this.contactPhone = contactPhone;
    }

    @Override
    protected int getCurrentVersion() {
        return VERSION;
    }
}
