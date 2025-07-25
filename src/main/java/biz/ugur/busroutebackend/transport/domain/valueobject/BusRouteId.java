package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class BusRouteId extends ValueObject {

    private final String value;

    public BusRouteId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Bus Route ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static BusRouteId generate() {
        return new BusRouteId(UUID.randomUUID().toString());
    }

    public static BusRouteId of(String value) {
        return new BusRouteId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
