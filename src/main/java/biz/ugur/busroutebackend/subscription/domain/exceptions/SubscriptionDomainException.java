package biz.ugur.busroutebackend.subscription.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;

public abstract class SubscriptionDomainException extends AbstractDomainException {

    protected SubscriptionDomainException(String errorCode, String message) {
        super("SUBSCRIPTION." + errorCode, message);
    }

    protected SubscriptionDomainException(String errorCode, String message, Severity severity) {
        super("SUBSCRIPTION." + errorCode, message, severity);
    }
}
