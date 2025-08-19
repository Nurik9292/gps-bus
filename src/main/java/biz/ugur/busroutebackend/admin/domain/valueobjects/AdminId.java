package biz.ugur.busroutebackend.admin.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class AdminId extends ValueObject {

    private final String value;

    public AdminId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Admin ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static AdminId generate() {
        return new AdminId(UUID.randomUUID().toString());
    }

    public static AdminId of(String value) {
        return new AdminId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}