package biz.ugur.busroutebackend.business.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;

public abstract class BusinessDomainException extends AbstractDomainException {

    protected BusinessDomainException(String errorCode, String message) {
        super("BUSINESS." + errorCode, message);
    }

    protected BusinessDomainException(String errorCode, String message, Severity severity) {
        super("BUSINESS." + errorCode, message, severity);
    }

    protected BusinessDomainException(String errorCode, String message, Severity severity, CorrelationId correlationId) {
        super("BUSINESS." + errorCode, message, severity, correlationId);
    }
}
