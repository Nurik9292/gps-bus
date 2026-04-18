package biz.ugur.busroutebackend.payment.domain.valueobjects;

import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class OrderNumber extends ValueObject {

    private static final int MAX_LENGTH = 32;

    private final String value;

    private OrderNumber(String value) {
        if (value == null || value.isBlank()) {
            throw new PaymentValidationException("orderNumber", "must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_LENGTH) {
            throw new PaymentValidationException("orderNumber",
                    "length must be <= " + MAX_LENGTH);
        }
        this.value = normalized;
    }

    public static OrderNumber generate() {
        return new OrderNumber(UUID.randomUUID().toString().replace("-", ""));
    }

    public static OrderNumber of(String value) {
        return new OrderNumber(value);
    }
}
