package biz.ugur.busroutebackend.advertising.domain.valueobjects;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class TariffName extends ValueObject {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 200;

    private final String value;

    private TariffName(String value) {
        if (value == null) {
            throw new AdvertisingValidationException("name", "must not be null");
        }
        String trimmed = value.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new AdvertisingValidationException("name",
                    "length must be between " + MIN_LENGTH + " and " + MAX_LENGTH);
        }
        this.value = trimmed;
    }

    public static TariffName of(String value) {
        return new TariffName(value);
    }
}
