package biz.ugur.busroutebackend.subscription.domain.exceptions;

import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionStatus;

public class SubscriptionStateTransitionException extends SubscriptionDomainException {

    public SubscriptionStateTransitionException(SubscriptionStatus from, SubscriptionStatus to) {
        super("INVALID_STATE_TRANSITION",
                "Subscription cannot transition from " + from + " to " + to,
                Severity.WARNING);
    }
}
