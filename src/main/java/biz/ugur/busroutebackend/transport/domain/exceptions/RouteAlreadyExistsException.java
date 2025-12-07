package biz.ugur.busroutebackend.transport.domain.exceptions;

import lombok.Getter;

@Getter
public class RouteAlreadyExistsException extends TransportDomainException {

    private final String identifier;
    private final String identifierType;

    public RouteAlreadyExistsException(String message) {
        super("ROUTE_ALREADY_EXISTS", message, Severity.WARNING);
        this.identifier = null;
        this.identifierType = null;
    }

    public RouteAlreadyExistsException(String identifier, String identifierType, String message) {
        super("ROUTE_ALREADY_EXISTS", message, Severity.WARNING);
        this.identifier = identifier;
        this.identifierType = identifierType;
    }

    public static RouteAlreadyExistsException byRouteNumber(String routeNumber) {
        return new RouteAlreadyExistsException(
                routeNumber,
                "route_number",
                String.format("Маршрут с номером '%s' уже существует", routeNumber)
        );
    }

    public static RouteAlreadyExistsException byRouteName(String routeName) {
        return new RouteAlreadyExistsException(
                routeName,
                "route_name",
                String.format("Маршрут с названием '%s' уже существует", routeName)
        );
    }
}
