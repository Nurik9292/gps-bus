package biz.ugur.busroutebackend.business.domain.exceptions;

import biz.ugur.busroutebackend.business.domain.enums.BusinessStatus;

public class BusinessStateTransitionException extends BusinessDomainException {

    public BusinessStateTransitionException(BusinessStatus from, BusinessStatus to) {
        super("ILLEGAL_STATE_TRANSITION",
                String.format("Illegal status transition: %s → %s", from, to),
                Severity.WARNING);
    }
}
