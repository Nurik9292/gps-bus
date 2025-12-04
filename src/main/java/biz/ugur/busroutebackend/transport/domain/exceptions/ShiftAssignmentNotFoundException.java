package biz.ugur.busroutebackend.transport.domain.exceptions;

import lombok.Getter;

@Getter
public class ShiftAssignmentNotFoundException extends TransportDomainException {

    private final String identifier;

    public ShiftAssignmentNotFoundException(String identifier) {
        super("SHIFT_ASSIGNMENT_NOT_FOUND", String.format("Shift assignment not found: %s", identifier), Severity.WARNING);
        this.identifier = identifier;
    }

    public static ShiftAssignmentNotFoundException byId(String id) {
        return new ShiftAssignmentNotFoundException(id);
    }
}
