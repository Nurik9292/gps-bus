package biz.ugur.busroutebackend.routing.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;


@Getter
public class InvalidLocationException extends RoutingDomainException {

    private final String locationType;
    private final String locationValue;

    public InvalidLocationException(String locationType, String locationValue) {
        super("INVALID_LOCATION", String.format("Invalid %s location: %s", locationType, locationValue), Severity.WARNING);
        this.locationType = locationType;
        this.locationValue = locationValue;
    }

    public InvalidLocationException(String locationType, String locationValue, CorrelationId correlationId) {
        super("INVALID_LOCATION", String.format("Invalid %s location: %s", locationType, locationValue), Severity.WARNING, correlationId);
        this.locationType = locationType;
        this.locationValue = locationValue;
    }

    public static InvalidLocationException invalidOrigin(String origin, CorrelationId correlationId) {
        return new InvalidLocationException("origin", origin, correlationId);
    }

    public static InvalidLocationException invalidDestination(String destination, CorrelationId correlationId) {
        return new InvalidLocationException("destination", destination, correlationId);
    }

    public static InvalidLocationException outOfBounds(String locationType, String coordinates, CorrelationId correlationId) {
        return new InvalidLocationException(
                locationType,
                String.format("%s (out of service area bounds)", coordinates),
                correlationId
        );
    }
}
