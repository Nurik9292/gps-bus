package biz.ugur.busroutebackend.integration.domain.exceptions;

import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import lombok.Getter;

@Getter
public class ExternalServiceBlockedException extends ExternalServiceException {

    private final String serviceIdentifier;
    private final String blockReason;

    public ExternalServiceBlockedException(ExternalServiceId id) {
        super("SERVICE_BLOCKED", "External service is blocked: " + id.getValue(), Severity.WARNING);
        this.serviceIdentifier = id.getValue();
        this.blockReason = null;
    }

    public ExternalServiceBlockedException(String serviceName) {
        super("SERVICE_BLOCKED", "External service is blocked: " + serviceName, Severity.WARNING);
        this.serviceIdentifier = serviceName;
        this.blockReason = null;
    }

    public ExternalServiceBlockedException(String serviceName, String reason) {
        super("SERVICE_BLOCKED",
              String.format("External service '%s' is blocked: %s", serviceName, reason),
              Severity.WARNING);
        this.serviceIdentifier = serviceName;
        this.blockReason = reason;
    }

    public static ExternalServiceBlockedException byId(ExternalServiceId id) {
        return new ExternalServiceBlockedException(id);
    }

    public static ExternalServiceBlockedException byName(String serviceName) {
        return new ExternalServiceBlockedException(serviceName);
    }

    public static ExternalServiceBlockedException withReason(String serviceName, String reason) {
        return new ExternalServiceBlockedException(serviceName, reason);
    }
}
