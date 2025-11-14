package biz.ugur.busroutebackend.integration.domain.events;

import lombok.Getter;


@Getter
public class ExternalServiceCreatedEvent extends ExternalServiceDomainEvent {

    public static final int CURRENT_VERSION = 1;

    private final String name;
    private final String maskedToken;
    private final String createdByAdminId;

    public ExternalServiceCreatedEvent(String externalServiceId,
                                       String name,
                                       String maskedToken,
                                       String createdByAdminId) {
        super(externalServiceId);
        this.name = name;
        this.maskedToken = maskedToken;
        this.createdByAdminId = createdByAdminId;
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }
}
