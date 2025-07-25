package biz.ugur.busroutebackend.admin.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class BannerId extends ValueObject {

    private final String value;

    public BannerId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Banner ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static BannerId generate() {
        return new BannerId(UUID.randomUUID().toString());
    }

    public static BannerId of(String value) {
        return new BannerId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}