package biz.ugur.busroutebackend.integration.domain.events;

import lombok.Getter;


@Getter
public class ExternalServiceDeletedEvent extends ExternalServiceDomainEvent {

    public static final int CURRENT_VERSION = 1;

    private final String name;
    private final String deletedByAdminId;

    public ExternalServiceDeletedEvent(String externalServiceId,
                                       String name,
                                       String deletedByAdminId) {
        super(externalServiceId);
        this.name = name;
        this.deletedByAdminId = deletedByAdminId;
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }
}
