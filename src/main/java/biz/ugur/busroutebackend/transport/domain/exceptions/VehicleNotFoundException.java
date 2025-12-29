package biz.ugur.busroutebackend.transport.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;


@Getter
public class VehicleNotFoundException extends TransportDomainException {

    private final String vehicleId;

    public VehicleNotFoundException(String vehicleId) {
        super("VEHICLE_NOT_FOUND", String.format("Vehicle not found with id: %s", vehicleId), Severity.WARNING);
        this.vehicleId = vehicleId;
    }

    public VehicleNotFoundException(String vehicleId, CorrelationId correlationId) {
        super("VEHICLE_NOT_FOUND", String.format("Vehicle not found with id: %s", vehicleId), Severity.WARNING, correlationId);
        this.vehicleId = vehicleId;
    }

    public static VehicleNotFoundException byId(String vehicleId) {
        return new VehicleNotFoundException(vehicleId);
    }
}
