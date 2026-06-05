package biz.ugur.busroutebackend.subscription.application.dto;

public enum PaymentOutcome {
    PENDING,
    SUCCESS,
    FAILED;

    public static PaymentOutcome fromPaymentStatus(String paymentStatus) {
        if (paymentStatus == null) {
            return PENDING;
        }
        return switch (paymentStatus) {
            case "COMPLETED" -> SUCCESS;
            case "REGISTERED", "PREAUTH" -> PENDING;
            case "DECLINED", "REVERSED", "EXPIRED", "CANCELLED", "REFUNDED" -> FAILED;
            default -> PENDING;
        };
    }
}
