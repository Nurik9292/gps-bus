package biz.ugur.busroutebackend.integration.domain.valueobjects;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode
public class ExternalServiceId implements Serializable {

    private final String value;

    private ExternalServiceId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ExternalServiceId cannot be null or empty");
        }
        this.value = value;
    }

    public static ExternalServiceId of(String value) {
        return new ExternalServiceId(value);
    }

    public static ExternalServiceId of(UUID uuid) {
        return new ExternalServiceId(uuid.toString());
    }

    public static ExternalServiceId generate() {
        return new ExternalServiceId(UUID.randomUUID().toString());
    }

    public UUID toUUID() {
        return UUID.fromString(value);
    }
}
