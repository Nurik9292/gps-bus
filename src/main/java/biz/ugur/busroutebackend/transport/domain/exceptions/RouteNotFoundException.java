package biz.ugur.busroutebackend.transport.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;


@Getter
public class RouteNotFoundException extends TransportDomainException {

    private final String identifier;
    private final String identifierType;

    public RouteNotFoundException(String identifier, String identifierType) {
        super("ROUTE_NOT_FOUND", String.format("Bus route not found with %s: %s", identifierType, identifier), Severity.WARNING);
        this.identifier = identifier;
        this.identifierType = identifierType;
    }

    public RouteNotFoundException(String identifier, String identifierType, CorrelationId correlationId) {
        super("ROUTE_NOT_FOUND", String.format("Bus route not found with %s: %s", identifierType, identifier), Severity.WARNING, correlationId);
        this.identifier = identifier;
        this.identifierType = identifierType;
    }

    public static RouteNotFoundException byId(Long id) {
        return new RouteNotFoundException(id.toString(), "id");
    }

    public static RouteNotFoundException byId(Long id, CorrelationId correlationId) {
        return new RouteNotFoundException(id.toString(), "id", correlationId);
    }

    public static RouteNotFoundException byRouteCode(String routeCode) {
        return new RouteNotFoundException(routeCode, "routeCode");
    }

    public static RouteNotFoundException byRouteCode(String routeCode, CorrelationId correlationId) {
        return new RouteNotFoundException(routeCode, "routeCode", correlationId);
    }
}
