package biz.ugur.busroutebackend.integration.domain.events;

import lombok.Getter;


@Getter
public class ExternalServiceUnblockedEvent extends ExternalServiceDomainEvent {

    public static final int CURRENT_VERSION = 1;

    private final String name;
    private final String unblockedByAdminId;

    public ExternalServiceUnblockedEvent(String externalServiceId,
                                         String name,
                                         String unblockedByAdminId) {
        super(externalServiceId);
        this.name = name;
        this.unblockedByAdminId = unblockedByAdminId;
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }
}
