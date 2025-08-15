package biz.ugur.busroutebackend.client.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteFavoriteId extends ValueObject {

    private final String value;

    public RouteFavoriteId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Route Favorite ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static RouteFavoriteId generate() {
        return new RouteFavoriteId(UUID.randomUUID().toString());
    }

    public static RouteFavoriteId of(String value) {
        return new RouteFavoriteId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}