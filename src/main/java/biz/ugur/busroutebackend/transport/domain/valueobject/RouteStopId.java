package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteStopId extends ValueObject {

    private final String value;

    public RouteStopId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Bus RouteStop ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static RouteStopId generate() {
        return new RouteStopId(UUID.randomUUID().toString());
    }

    public static RouteStopId of(String value) {
        return new RouteStopId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
