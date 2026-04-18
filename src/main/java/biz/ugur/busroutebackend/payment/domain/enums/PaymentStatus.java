package biz.ugur.busroutebackend.payment.domain.enums;

import lombok.Getter;


@Getter
public enum PaymentStatus {

    REGISTERED("Registered, awaiting customer payment"),
    PREAUTH("Two-phase hold in place"),
    COMPLETED("Successfully paid"),
    DECLINED("Declined by bank"),
    REVERSED("Authorization cancelled"),
    REFUNDED("Refunded to customer"),
    EXPIRED("Session timed out"),
    CANCELLED("Cancelled by admin");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    public boolean isTerminal() {
        return switch (this) {
            case COMPLETED, DECLINED, REVERSED, REFUNDED, EXPIRED, CANCELLED -> true;
            case REGISTERED, PREAUTH -> false;
        };
    }

    public boolean canTransitionTo(PaymentStatus target) {
        if (this == target) return false;
        return switch (this) {
            case REGISTERED ->
                    target == PREAUTH || target == COMPLETED || target == DECLINED
                            || target == EXPIRED || target == CANCELLED;
            case PREAUTH ->
                    target == COMPLETED || target == DECLINED || target == REVERSED;
            case COMPLETED ->
                    target == REFUNDED || target == REVERSED;
            case DECLINED, REVERSED, REFUNDED, EXPIRED, CANCELLED -> false;
        };
    }


    public static PaymentStatus fromSvEpgOrderStatus(int code) {
        return switch (code) {
            case 0, 5 -> REGISTERED;
            case 1    -> PREAUTH;
            case 2    -> COMPLETED;
            case 3    -> REVERSED;
            case 4    -> REFUNDED;
            case 6    -> DECLINED;
            default   -> REGISTERED;
        };
    }
}
