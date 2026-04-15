package biz.ugur.busroutebackend.advertising.domain.valueobjects;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class TariffId extends ValueObject {

    private final String value;

    private TariffId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new AdvertisingValidationException("tariffId", "must not be blank");
        }
        this.value = value.trim();
    }

    public static TariffId generate() {
        return new TariffId(UUID.randomUUID().toString());
    }

    public static TariffId of(String value) {
        return new TariffId(value);
    }
}
