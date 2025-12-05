package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteAlternativeId extends ValueObject {

    private final String value;

    public RouteAlternativeId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Route Alternative ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static RouteAlternativeId generate() {
        return new RouteAlternativeId(UUID.randomUUID().toString());
    }

    public static RouteAlternativeId of(String value) {
        return new RouteAlternativeId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
