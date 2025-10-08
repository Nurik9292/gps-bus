package biz.ugur.busroutebackend.routing.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;


@Getter
public class RouteCalculationException extends RoutingDomainException {

    private final String reason;

    public RouteCalculationException(String reason) {
        super("ROUTE_CALCULATION_ERROR", String.format("Route calculation failed: %s", reason), Severity.ERROR);
        this.reason = reason;
    }

    public RouteCalculationException(String reason, CorrelationId correlationId) {
        super("ROUTE_CALCULATION_ERROR", String.format("Route calculation failed: %s", reason), Severity.ERROR, correlationId);
        this.reason = reason;
    }

    public static RouteCalculationException graphBuildingFailed(CorrelationId correlationId) {
        return new RouteCalculationException("Failed to build routing graph", correlationId);
    }

    public static RouteCalculationException algorithmFailed(String algorithmName, CorrelationId correlationId) {
        return new RouteCalculationException(String.format("Algorithm '%s' failed", algorithmName), correlationId);
    }
}
