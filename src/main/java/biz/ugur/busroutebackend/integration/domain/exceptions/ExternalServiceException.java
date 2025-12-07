package biz.ugur.busroutebackend.integration.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;

public abstract class ExternalServiceException extends AbstractDomainException {

    protected ExternalServiceException(String errorCode, String message) {
        super("INTEGRATION." + errorCode, message);
    }

    protected ExternalServiceException(String errorCode, String message, Severity severity) {
        super("INTEGRATION." + errorCode, message, severity);
    }

    protected ExternalServiceException(String errorCode, String message, Severity severity, CorrelationId correlationId) {
        super("INTEGRATION." + errorCode, message, severity, correlationId);
    }

    protected static String maskToken(String token) {
        if (token == null || token.length() <= 12) {
            return "****";
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 4);
    }
}
