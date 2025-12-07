package biz.ugur.busroutebackend.notification.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;

public abstract class NotificationDomainException extends AbstractDomainException {

    protected NotificationDomainException(String errorCode, String message) {
        super("NOTIFICATION." + errorCode, message);
    }

    protected NotificationDomainException(String errorCode, String message, Severity severity) {
        super("NOTIFICATION." + errorCode, message, severity);
    }

    protected NotificationDomainException(String errorCode, String message, Severity severity, CorrelationId correlationId) {
        super("NOTIFICATION." + errorCode, message, severity, correlationId);
    }
}
