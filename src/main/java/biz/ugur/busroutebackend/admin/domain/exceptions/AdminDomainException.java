package biz.ugur.busroutebackend.admin.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.AbstractDomainException;
import biz.ugur.busroutebackend.shared.domain.CorrelationId;

public abstract class AdminDomainException extends AbstractDomainException {

    protected AdminDomainException(String errorCode, String message) {
        super(errorCode, message);
    }

    protected AdminDomainException(String errorCode, String message, Severity severity) {
        super(errorCode, message, severity);
    }

    protected AdminDomainException(String errorCode, String message, Severity severity, CorrelationId correlationId) {
        super(errorCode, message, severity, correlationId);
    }

}