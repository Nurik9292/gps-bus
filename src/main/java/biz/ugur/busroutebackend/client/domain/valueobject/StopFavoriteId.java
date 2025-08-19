package biz.ugur.busroutebackend.client.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class StopFavoriteId extends ValueObject {

    private final String value;

    public StopFavoriteId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Stop Favorite ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static StopFavoriteId generate() {
        return new StopFavoriteId(UUID.randomUUID().toString());
    }

    public static StopFavoriteId of(String value) {
        return new StopFavoriteId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}