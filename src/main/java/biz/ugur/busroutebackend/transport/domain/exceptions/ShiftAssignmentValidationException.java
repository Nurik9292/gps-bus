package biz.ugur.busroutebackend.transport.domain.exceptions;

import lombok.Getter;

@Getter
public class ShiftAssignmentValidationException extends TransportDomainException {

    public ShiftAssignmentValidationException(String message) {
        super("SHIFT_ASSIGNMENT_VALIDATION", message, Severity.WARNING);
    }

    public static ShiftAssignmentValidationException vehicleNotFound(String vehicleId) {
        return new ShiftAssignmentValidationException(String.format("Vehicle not found: %s", vehicleId));
    }

    public static ShiftAssignmentValidationException routeNotFound(String routeId) {
        return new ShiftAssignmentValidationException(String.format("Route not found: %s", routeId));
    }

    public static ShiftAssignmentValidationException alreadyExists(String vehicleId, String shiftType) {
        return new ShiftAssignmentValidationException(
                String.format("Vehicle %s already has assignment for shift %s", vehicleId, shiftType));
    }
}
