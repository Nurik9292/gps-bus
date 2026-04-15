package biz.ugur.busroutebackend.payment.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;

public abstract class PaymentDomainException extends AbstractDomainException {

    protected PaymentDomainException(String errorCode, String message) {
        super("PAYMENT." + errorCode, message);
    }

    protected PaymentDomainException(String errorCode, String message, Severity severity) {
        super("PAYMENT." + errorCode, message, severity);
    }
}
