package biz.ugur.busroutebackend.payment.domain.exceptions;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;

public class PaymentStateTransitionException extends PaymentDomainException {

    public PaymentStateTransitionException(PaymentStatus from, PaymentStatus to) {
        super("ILLEGAL_STATE_TRANSITION",
                String.format("Illegal payment status transition: %s → %s", from, to),
                Severity.WARNING);
    }
}
