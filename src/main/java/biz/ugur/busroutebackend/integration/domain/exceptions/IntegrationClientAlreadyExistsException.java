package biz.ugur.busroutebackend.integration.domain.exceptions;

import lombok.Getter;


@Getter
public class IntegrationClientAlreadyExistsException extends ExternalServiceException {

    private final String serviceId;
    private final String externalUserId;

    public IntegrationClientAlreadyExistsException(String serviceId, String externalUserId) {
        super(
                "CLIENT_ALREADY_EXISTS",
                "Client with external user ID '" + externalUserId + "' already exists for this service",
                Severity.WARNING
        );
        this.serviceId = serviceId;
        this.externalUserId = externalUserId;
    }
}
