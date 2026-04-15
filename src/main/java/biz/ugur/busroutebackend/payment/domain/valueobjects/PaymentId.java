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
public class PaymentId extends ValueObject {

    private final String value;

    private PaymentId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentValidationException("paymentId", "must not be blank");
        }
        this.value = value.trim();
    }

    public static PaymentId generate() {
        return new PaymentId(UUID.randomUUID().toString());
    }

    public static PaymentId of(String value) {
        return new PaymentId(value);
    }
}
