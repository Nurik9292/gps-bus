package biz.ugur.busroutebackend.admin.infrastructure.exception;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminDomainException;
import biz.ugur.busroutebackend.shared.domain.CorrelationId;
import lombok.Getter;

@Getter
public class AdminRepositoryException extends AdminDomainException {

    public enum RepositoryErrorType {
        CONNECTION_FAILED, CONSTRAINT_VIOLATION, DATA_INTEGRITY, TIMEOUT, UNKNOWN_ERROR
    }

    private final RepositoryErrorType errorType;
    private final String operation;
    private final Throwable rootCause;

    public AdminRepositoryException(RepositoryErrorType errorType, String operation, Throwable cause) {
        super("REPOSITORY_ERROR",
                String.format("Repository operation '%s' failed: %s", operation, errorType),
                Severity.CRITICAL);
        this.errorType = errorType;
        this.operation = operation;
        this.rootCause = cause;
    }

    public AdminRepositoryException(RepositoryErrorType errorType, String operation,
                                    String customMessage, CorrelationId correlationId) {
        super("REPOSITORY_ERROR", customMessage, Severity.ERROR, correlationId);
        this.errorType = errorType;
        this.operation = operation;
        this.rootCause = null;
    }

}