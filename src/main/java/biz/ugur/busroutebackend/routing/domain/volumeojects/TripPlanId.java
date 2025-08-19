package biz.ugur.busroutebackend.routing.domain.volumeojects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class TripPlanId extends ValueObject {

    private final String value;

    public TripPlanId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Trip Plan ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static TripPlanId generate() {
        return new TripPlanId(UUID.randomUUID().toString());
    }

    public static TripPlanId of(String value) {
        return new TripPlanId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}