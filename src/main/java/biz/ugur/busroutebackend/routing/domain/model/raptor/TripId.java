package biz.ugur.busroutebackend.routing.domain.model.raptor;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class TripId extends ValueObject {

    private final String value;

    public TripId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Trip ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static TripId generate() {
        return new TripId(UUID.randomUUID().toString());
    }

    public static TripId of(String value) {
        return new TripId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
