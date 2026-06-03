package biz.ugur.busroutebackend.subscription.domain.enums;

import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionValidationException;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum SubscriptionStatus {

    PENDING("Awaiting payment"),
    ACTIVE("Active, paid"),
    EXPIRED("Expired by time"),
    CANCELLED("Cancelled by client or payment failure");

    private final String description;

    SubscriptionStatus(String description) {
        this.description = description;
    }

    public boolean isTerminal() {
        return this == EXPIRED || this == CANCELLED;
    }

    public boolean canTransitionTo(SubscriptionStatus target) {
        if (this == target) return false;
        return switch (this) {
            case PENDING   -> target == ACTIVE || target == CANCELLED;
            case ACTIVE    -> target == EXPIRED || target == CANCELLED;
            case EXPIRED, CANCELLED -> false;
        };
    }

    public static SubscriptionStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new SubscriptionValidationException("status", "must not be blank");
        }
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseThrow(() -> new SubscriptionValidationException(
                        "status", "unknown status: " + raw));
    }
}
