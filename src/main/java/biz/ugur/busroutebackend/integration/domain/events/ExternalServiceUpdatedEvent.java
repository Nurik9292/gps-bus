package biz.ugur.busroutebackend.integration.domain.events;

import lombok.Getter;

import java.util.List;


@Getter
public class ExternalServiceUpdatedEvent extends ExternalServiceDomainEvent {

    public static final int CURRENT_VERSION = 1;

    private final String name;
    private final String description;
    private final List<String> allowedEndpoints;
    private final Integer rateLimitPerMinute;

    public ExternalServiceUpdatedEvent(String externalServiceId,
                                       String name,
                                       String description,
                                       List<String> allowedEndpoints,
                                       Integer rateLimitPerMinute) {
        super(externalServiceId);
        this.name = name;
        this.description = description;
        this.allowedEndpoints = allowedEndpoints;
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }
}
