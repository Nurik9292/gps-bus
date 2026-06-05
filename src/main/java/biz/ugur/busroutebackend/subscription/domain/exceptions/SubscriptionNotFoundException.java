package biz.ugur.busroutebackend.subscription.domain.exceptions;

public class SubscriptionNotFoundException extends SubscriptionDomainException {

    public SubscriptionNotFoundException(String message) {
        super("NOT_FOUND", message, Severity.WARNING);
    }
}
