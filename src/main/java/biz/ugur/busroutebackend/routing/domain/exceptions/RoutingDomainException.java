package biz.ugur.busroutebackend.routing.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;


public class RoutingDomainException extends AbstractDomainException {

    protected RoutingDomainException(String errorCode, String message) {
        super("ROUTING." + errorCode, message);
    }

    protected RoutingDomainException(String errorCode, String message, Severity severity) {
        super("ROUTING." + errorCode, message, severity);
    }

    protected RoutingDomainException(String errorCode, String message, Severity severity, CorrelationId correlationId) {
        super("ROUTING." + errorCode, message, severity, correlationId);
    }
}
