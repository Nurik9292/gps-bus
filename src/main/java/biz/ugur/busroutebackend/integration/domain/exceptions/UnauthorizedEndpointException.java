package biz.ugur.busroutebackend.integration.domain.exceptions;

/**
 * Exception thrown when external service attempts to access an unauthorized endpoint
 */
public class UnauthorizedEndpointException extends ExternalServiceException {

    public UnauthorizedEndpointException(String serviceName, String endpoint) {
        super(String.format("External service '%s' is not authorized to access endpoint: %s",
            serviceName, endpoint));
    }
}
