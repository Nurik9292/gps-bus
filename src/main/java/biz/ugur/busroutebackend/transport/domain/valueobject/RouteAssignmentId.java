package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteAssignmentId extends ValueObject {

    private final String value;

    public RouteAssignmentId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Route assignment ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static RouteAssignmentId generate() {
        return new RouteAssignmentId(UUID.randomUUID().toString());
    }

    public static RouteAssignmentId of(String value) {
        return new RouteAssignmentId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
