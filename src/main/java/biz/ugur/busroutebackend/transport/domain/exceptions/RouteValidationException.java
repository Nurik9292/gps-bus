package biz.ugur.busroutebackend.transport.domain.exceptions;


public class RouteValidationException extends TransportDomainException {

    public RouteValidationException(String field, String message) {
        super("ROUTE_VALIDATION_ERROR", String.format("Validation failed for field '%s': %s", field, message), Severity.WARNING);
    }

    public RouteValidationException(String message) {
        super("ROUTE_VALIDATION_ERROR", message, Severity.WARNING);
    }
}
