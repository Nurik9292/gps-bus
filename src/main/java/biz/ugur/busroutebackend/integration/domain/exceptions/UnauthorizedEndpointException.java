package biz.ugur.busroutebackend.integration.domain.exceptions;

import lombok.Getter;


@Getter
public class UnauthorizedEndpointException extends ExternalServiceException {

    private final String serviceName;
    private final String endpoint;

    public UnauthorizedEndpointException(String serviceName, String endpoint) {
        super("UNAUTHORIZED_ENDPOINT",
              String.format("External service '%s' is not authorized to access endpoint: %s", serviceName, endpoint),
              Severity.WARNING);
        this.serviceName = serviceName;
        this.endpoint = endpoint;
    }

    public static UnauthorizedEndpointException forEndpoint(String serviceName, String endpoint) {
        return new UnauthorizedEndpointException(serviceName, endpoint);
    }
}
