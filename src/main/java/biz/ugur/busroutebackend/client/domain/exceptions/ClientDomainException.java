package biz.ugur.busroutebackend.client.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;


public class ClientDomainException extends AbstractDomainException {

    protected ClientDomainException(String errorCode, String message) {
        super("CLIENT." + errorCode, message);
    }

    protected ClientDomainException(String errorCode, String message, Severity severity) {
        super("CLIENT." + errorCode, message, severity);
    }

    protected ClientDomainException(String errorCode, String message, Severity severity, CorrelationId correlationId) {
        super("CLIENT." + errorCode, message, severity, correlationId);
    }
}
