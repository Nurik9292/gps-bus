package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class CityId extends ValueObject {

    private final String value;

    public CityId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("City ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static CityId of(String value) {
        return new CityId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
