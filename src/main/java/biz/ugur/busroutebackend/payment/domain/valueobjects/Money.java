package biz.ugur.busroutebackend.payment.domain.valueobjects;

import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class Money extends ValueObject {

    private static final int MINOR_UNITS_PER_MAJOR = 100;

    private final long amountMinor;
    private final String currency;

    private Money(long amountMinor, String currency) {
        if (amountMinor <= 0) {
            throw new PaymentValidationException("amount", "must be strictly positive");
        }
        if (currency == null || currency.trim().length() != 3) {
            throw new PaymentValidationException("currency", "must be a 3-letter code");
        }
        this.amountMinor = amountMinor;
        this.currency = currency.trim().toUpperCase();
    }

    public static Money ofMinor(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

    public int getIso4217NumericCode() {
        return switch (currency) {
            case "TMT" -> 934;
            case "USD" -> 840;
            case "EUR" -> 978;
            case "RUB" -> 643;
            default -> throw new PaymentValidationException("currency",
                    "ISO 4217 numeric code unknown for: " + currency);
        };
    }

    public double toMajorUnits() {
        return amountMinor / (double) MINOR_UNITS_PER_MAJOR;
    }
}
