package biz.ugur.busroutebackend.client.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ClientId extends ValueObject {

    private final String value;

    public ClientId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static ClientId generate() {
        return new ClientId(UUID.randomUUID().toString());
    }

    public static ClientId of(String value) {
        return new ClientId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}