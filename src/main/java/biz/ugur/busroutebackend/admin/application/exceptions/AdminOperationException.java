package biz.ugur.busroutebackend.admin.application.exceptions;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminDomainException;
import biz.ugur.busroutebackend.shared.domain.CorrelationId;
import lombok.Getter;

@Getter
public class AdminOperationException extends AdminDomainException {

    public enum OperationType {
        CREATE, UPDATE, DELETE, AUTHENTICATE, AUTHORIZE, SEARCH, EXPORT, IMPORT
    }

    private final OperationType operationType;
    private final String operationContext;
    private final Throwable rootCause;

    public AdminOperationException(OperationType operationType, String message) {
        super("OPERATION_FAILED",
                String.format("Operation '%s' failed: %s", operationType, message),
                Severity.ERROR);
        this.operationType = operationType;
        this.operationContext = null;
        this.rootCause = null;
    }

    public AdminOperationException(OperationType operationType, String message, Throwable cause) {
        super("OPERATION_FAILED",
                String.format("Operation '%s' failed: %s", operationType, message),
                Severity.ERROR);
        this.operationType = operationType;
        this.operationContext = null;
        this.rootCause = cause;
    }

    public AdminOperationException(OperationType operationType, String operationContext,
                                   String message, CorrelationId correlationId) {
        super("OPERATION_FAILED",
                String.format("Operation '%s' in context '%s' failed: %s",
                        operationType, operationContext, message),
                Severity.ERROR,
                correlationId);
        this.operationType = operationType;
        this.operationContext = operationContext;
        this.rootCause = null;
    }

}
