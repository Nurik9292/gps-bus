package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ImmediateAssignmentId extends ValueObject {

    private final String value;

    public ImmediateAssignmentId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Immediate Assignment ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public ImmediateAssignmentId(UUID value) {
        this(value.toString());
    }

    public static ImmediateAssignmentId generate() {
        return new ImmediateAssignmentId(UUID.randomUUID().toString());
    }

    public static ImmediateAssignmentId from(String value) {
        return new ImmediateAssignmentId(value);
    }

    public UUID toUUID() {
        return UUID.fromString(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
