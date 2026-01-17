package biz.ugur.busroutebackend.geospatial.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;


@Getter
@EqualsAndHashCode(callSuper = false)
public class GarageId extends ValueObject {

    private final UUID value;

    public GarageId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Garage ID cannot be null or empty");
        }
        this.value = UUID.fromString(value.trim());
    }

    public GarageId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("Garage ID cannot be null");
        }
        this.value = value;
    }

    public GarageId() {
        this.value = UUID.randomUUID();
    }

    public static GarageId generate() {
        return new GarageId();
    }

    public static GarageId of(String value) {
        return new GarageId(value);
    }

    public static GarageId of(UUID value) {
        return new GarageId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
