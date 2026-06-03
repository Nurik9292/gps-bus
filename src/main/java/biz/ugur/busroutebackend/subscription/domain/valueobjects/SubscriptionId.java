package biz.ugur.busroutebackend.subscription.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class SubscriptionId extends ValueObject {

    private final String value;

    private SubscriptionId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new SubscriptionValidationException("subscriptionId", "must not be blank");
        }
        this.value = value.trim();
    }

    public static SubscriptionId generate() {
        return new SubscriptionId(UUID.randomUUID().toString());
    }

    public static SubscriptionId of(String value) {
        return new SubscriptionId(value);
    }
}
