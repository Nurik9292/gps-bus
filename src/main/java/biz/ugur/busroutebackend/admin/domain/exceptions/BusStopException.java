package biz.ugur.busroutebackend.admin.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.AbstractDomainException;
import biz.ugur.busroutebackend.shared.domain.CorrelationId;

public class BusStopException extends AbstractDomainException {

    protected BusStopException(String errorCode, String message) {
        super(errorCode, message);
    }

    protected BusStopException(String errorCode, String message, Severity severity) {
        super(errorCode, message, severity);
    }

    protected BusStopException(String errorCode, String message, Severity severity, CorrelationId correlationId) {
        super(errorCode, message, severity, correlationId);
    }
}
