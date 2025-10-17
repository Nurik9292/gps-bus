package biz.ugur.busroutebackend.routing.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;


@Getter
public class TripPlanningException extends RoutingDomainException {

    @Getter
    public enum PlanningErrorType {
        NO_ROUTE_FOUND("No route found between origin and destination"),
        INVALID_ORIGIN("Invalid origin location"),
        INVALID_DESTINATION("Invalid destination location"),
        SAME_ORIGIN_DESTINATION("Origin and destination are the same"),
        DISTANCE_TOO_LONG("Distance between origin and destination is too long"),
        DISTANCE_TOO_SHORT("Minimum distance is 100 meters"),
        LOCATION_OUT_OF_BOUNDS("Locations must be within Turkmenistan"),
        MISSING_LOCATION("From and To locations are required"),
        NO_SERVICE_AVAILABLE("No bus service available for the requested time"),
        INVALID_TIME("Invalid departure or arrival time"),
        TOO_MANY_TRANSFERS("No route found with acceptable number of transfers"),
        CALCULATION_TIMEOUT("Route calculation timed out");

        private final String defaultMessage;

        PlanningErrorType(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }
    }

    private final PlanningErrorType planningErrorType;
    private final String origin;
    private final String destination;

    public TripPlanningException(PlanningErrorType planningErrorType) {
        super("TRIP_PLANNING_ERROR", planningErrorType.getDefaultMessage(), Severity.WARNING);
        this.planningErrorType = planningErrorType;
        this.origin = null;
        this.destination = null;
    }

    public TripPlanningException(PlanningErrorType planningErrorType, CorrelationId correlationId) {
        super("TRIP_PLANNING_ERROR", planningErrorType.getDefaultMessage(), Severity.WARNING, correlationId);
        this.planningErrorType = planningErrorType;
        this.origin = null;
        this.destination = null;
    }

    public TripPlanningException(PlanningErrorType planningErrorType, String origin, String destination, CorrelationId correlationId) {
        super("TRIP_PLANNING_ERROR",
                String.format("%s (From: %s, To: %s)", planningErrorType.getDefaultMessage(), origin, destination),
                Severity.WARNING,
                correlationId);
        this.planningErrorType = planningErrorType;
        this.origin = origin;
        this.destination = destination;
    }

    public static TripPlanningException noRouteFound(String origin, String destination, CorrelationId correlationId) {
        return new TripPlanningException(PlanningErrorType.NO_ROUTE_FOUND, origin, destination, correlationId);
    }

    public static TripPlanningException sameOriginDestination(CorrelationId correlationId) {
        return new TripPlanningException(PlanningErrorType.SAME_ORIGIN_DESTINATION, correlationId);
    }

    public static TripPlanningException calculationTimeout(CorrelationId correlationId) {
        return new TripPlanningException(PlanningErrorType.CALCULATION_TIMEOUT, correlationId);
    }

    public boolean isRetryable() {
        return planningErrorType == PlanningErrorType.CALCULATION_TIMEOUT;
    }
}
