package biz.ugur.busroutebackend.subscription.domain.enums;

import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionValidationException;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum SubscriptionPeriod {

    MONTHLY("month", 30),
    YEARLY("year", 365);

    private final String mobileAlias;
    private final int daysCount;

    SubscriptionPeriod(String mobileAlias, int daysCount) {
        this.mobileAlias = mobileAlias;
        this.daysCount = daysCount;
    }

    public static SubscriptionPeriod from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new SubscriptionValidationException("period", "must not be blank");
        }
        String normalized = raw.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(normalized)
                        || p.mobileAlias.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new SubscriptionValidationException(
                        "period", "unknown period: " + raw + " (expected 'month' or 'year')"));
    }
}
