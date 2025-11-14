package biz.ugur.busroutebackend.integration.domain.events;

import lombok.Getter;


@Getter
public class ExternalServiceBlockedEvent extends ExternalServiceDomainEvent {

    public static final int CURRENT_VERSION = 1;

    private final String name;
    private final String blockedByAdminId;
    private final String reason;

    public ExternalServiceBlockedEvent(String externalServiceId,
                                       String name,
                                       String blockedByAdminId,
                                       String reason) {
        super(externalServiceId);
        this.name = name;
        this.blockedByAdminId = blockedByAdminId;
        this.reason = reason;
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }
}
