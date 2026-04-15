package biz.ugur.busroutebackend.payment.domain.enums;

import lombok.Getter;

/**
 * Payment lifecycle.
 *
 * <pre>
 *   REGISTERED ──┬── COMPLETED ──┬── REFUNDED  (full/partial customer refund)
 *                │               └── REVERSED  (authorization cancelled within merchant window)
 *                ├── DECLINED    (bank rejected)
 *                ├── EXPIRED     (session timed out before payment)
 *                └── CANCELLED   (admin cancelled manually)
 *
 *   PREAUTH → COMPLETED / DECLINED / REVERSED   (only for two-phase flows; unused today)
 * </pre>
 */
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

    /**
     * Map sv_epg OrderStatus numeric codes (from getOrderStatus.do) to our domain status.
     * <table>
     *   <tr><th>sv_epg code</th><th>Meaning</th><th>Our status</th></tr>
     *   <tr><td>0</td><td>Registered, not paid</td><td>REGISTERED</td></tr>
     *   <tr><td>1</td><td>Preauth hold</td><td>PREAUTH</td></tr>
     *   <tr><td>2</td><td>Deposited successfully</td><td>COMPLETED</td></tr>
     *   <tr><td>3</td><td>Reversed</td><td>REVERSED</td></tr>
     *   <tr><td>4</td><td>Refunded</td><td>REFUNDED</td></tr>
     *   <tr><td>5</td><td>3DS in progress</td><td>REGISTERED</td></tr>
     *   <tr><td>6</td><td>Declined</td><td>DECLINED</td></tr>
     * </table>
     */
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
