package biz.ugur.busroutebackend.shared.domain.exception;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;

import java.time.Instant;

@Getter
public abstract class AbstractDomainException extends RuntimeException implements DomainException {
    protected final String errorCode;
    protected final Instant timestamp;
    protected final CorrelationId correlationId;
    protected final Severity severity;

    protected AbstractDomainException (String errorCode, String message) {
        this(errorCode, message, Severity.ERROR, CorrelationId.generate());
    }

    protected AbstractDomainException (String errorCode, String message, Severity severity) {
        this(errorCode, message, severity, CorrelationId.generate());
    }

    protected AbstractDomainException(String errorCode, String message, Severity severity, CorrelationId correlationId) {
        super(message);
        this.errorCode = errorCode;
        this.timestamp = Instant.now();
        this.correlationId = correlationId;
        this.severity = severity;
    }

    public String getBoundedContext() {
        if (errorCode.contains(".")) {
            return errorCode.substring(0, errorCode.indexOf(".")).toLowerCase();
        }
        return "unknown";
    }

}