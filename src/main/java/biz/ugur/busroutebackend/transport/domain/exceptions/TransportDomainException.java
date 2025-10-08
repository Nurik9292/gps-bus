package biz.ugur.busroutebackend.transport.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;


public class TransportDomainException extends AbstractDomainException {

    protected TransportDomainException(String errorCode, String message) {
        super("TRANSPORT." + errorCode, message);
    }

    protected TransportDomainException(String errorCode, String message, Severity severity) {
        super("TRANSPORT." + errorCode, message, severity);
    }

    protected TransportDomainException(String errorCode, String message, Severity severity, CorrelationId correlationId) {
        super("TRANSPORT." + errorCode, message, severity, correlationId);
    }
}
