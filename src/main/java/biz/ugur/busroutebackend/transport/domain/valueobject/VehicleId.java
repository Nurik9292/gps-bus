package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class VehicleId extends ValueObject {

    private final String value;

    public VehicleId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Vehicle ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static VehicleId generate() {
        return new VehicleId(UUID.randomUUID().toString());
    }

    public static VehicleId of(String value) {
        return new VehicleId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}