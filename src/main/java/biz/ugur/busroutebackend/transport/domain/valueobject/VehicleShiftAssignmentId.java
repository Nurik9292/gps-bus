package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class VehicleShiftAssignmentId extends ValueObject {

    private final String value;

    public VehicleShiftAssignmentId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("VehicleShiftAssignment ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static VehicleShiftAssignmentId generate() {
        return new VehicleShiftAssignmentId(UUID.randomUUID().toString());
    }

    public static VehicleShiftAssignmentId of(String value) {
        return new VehicleShiftAssignmentId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
