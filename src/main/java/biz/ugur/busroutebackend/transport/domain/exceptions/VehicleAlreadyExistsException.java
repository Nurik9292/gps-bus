package biz.ugur.busroutebackend.transport.domain.exceptions;

import lombok.Getter;

@Getter
public class VehicleAlreadyExistsException extends TransportDomainException {

    private final String identifier;

    public VehicleAlreadyExistsException(String message) {
        super("VEHICLE_ALREADY_EXISTS", message, Severity.WARNING);
        this.identifier = null;
    }

    public VehicleAlreadyExistsException(String identifier, String message) {
        super("VEHICLE_ALREADY_EXISTS", message, Severity.WARNING);
        this.identifier = identifier;
    }
}
