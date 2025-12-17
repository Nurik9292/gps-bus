package biz.ugur.busroutebackend.integration.domain.exceptions;

import lombok.Getter;


@Getter
public class IntegrationClientNotFoundException extends ExternalServiceException {

    private final String identifier;
    private final String identifierType;

    private IntegrationClientNotFoundException(String identifier, String identifierType, String message) {
        super("CLIENT_NOT_FOUND", message, Severity.WARNING);
        this.identifier = identifier;
        this.identifierType = identifierType;
    }

    public static IntegrationClientNotFoundException byClientId(String clientId) {
        return new IntegrationClientNotFoundException(
                clientId,
                "clientId",
                "Integration client not found: " + clientId
        );
    }

    public static IntegrationClientNotFoundException byExternalUserId(String serviceId, String externalUserId) {
        return new IntegrationClientNotFoundException(
                externalUserId,
                "externalUserId",
                "Client with external user ID '" + externalUserId + "' not found for service: " + serviceId
        );
    }
}
