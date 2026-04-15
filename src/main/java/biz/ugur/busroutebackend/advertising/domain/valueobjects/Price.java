package biz.ugur.busroutebackend.advertising.domain.valueobjects;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Monetary amount stored in minor currency units (tiyin for TMT, cents for USD).
 * Storing the price as long integer avoids float/double rounding drift.
 */
@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class Price extends ValueObject {

    private static final int MINOR_UNITS_PER_MAJOR = 100;

    private final long amountMinor;
    private final String currency;

    private Price(long amountMinor, String currency) {
        if (amountMinor < 0) {
            throw new AdvertisingValidationException("price", "amount must be non-negative");
        }
        if (currency == null || currency.trim().length() != 3) {
            throw new AdvertisingValidationException("currency", "must be a 3-letter code");
        }
        this.amountMinor = amountMinor;
        this.currency = currency.trim().toUpperCase();
    }

    public static Price ofMinor(long amountMinor, String currency) {
        return new Price(amountMinor, currency);
    }

    public static Price ofMajor(double amountMajor, String currency) {
        return new Price(Math.round(amountMajor * MINOR_UNITS_PER_MAJOR), currency);
    }

    public double toMajorUnits() {
        return amountMinor / (double) MINOR_UNITS_PER_MAJOR;
    }
}
