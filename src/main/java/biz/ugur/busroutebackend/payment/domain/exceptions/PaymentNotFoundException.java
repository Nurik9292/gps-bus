package biz.ugur.busroutebackend.payment.domain.exceptions;

public class PaymentNotFoundException extends PaymentDomainException {

    public PaymentNotFoundException(String paymentId) {
        super("NOT_FOUND", "Payment not found: " + paymentId, Severity.WARNING);
    }
}
