package biz.ugur.busroutebackend.integration.domain.exceptions;

import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import lombok.Getter;

@Getter
public class ExternalServiceNotFoundException extends ExternalServiceException {

    private final String serviceIdentifier;
    private final String identifierType;

    public ExternalServiceNotFoundException(ExternalServiceId id) {
        super("SERVICE_NOT_FOUND", "External service not found: " + id.getValue(), Severity.WARNING);
        this.serviceIdentifier = id.getValue();
        this.identifierType = "id";
    }

    public ExternalServiceNotFoundException(String apiToken) {
        super("SERVICE_NOT_FOUND", "External service not found for token: " + maskToken(apiToken), Severity.WARNING);
        this.serviceIdentifier = maskToken(apiToken);
        this.identifierType = "token";
    }

    public static ExternalServiceNotFoundException byId(ExternalServiceId id) {
        return new ExternalServiceNotFoundException(id);
    }

    public static ExternalServiceNotFoundException byToken(String apiToken) {
        return new ExternalServiceNotFoundException(apiToken);
    }
}
