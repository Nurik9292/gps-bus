package biz.ugur.busroutebackend.admin.application.exceptions;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminDomainException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;

@Getter
public class AdminConcurrencyException extends AdminDomainException {

    private final String entityId;
    private final long expectedVersion;
    private final long actualVersion;
    private final String conflictingOperation;

    public AdminConcurrencyException(String entityId, long expectedVersion, long actualVersion) {
        super("OPTIMISTIC_LOCK_FAILURE",
                String.format("Optimistic lock failure for admin '%s': expected version %d, actual version %d",
                        entityId, expectedVersion, actualVersion));
        this.entityId = entityId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
        this.conflictingOperation = null;
    }

    public AdminConcurrencyException(String entityId, String conflictingOperation) {
        super("CONCURRENT_MODIFICATION",
                String.format("Admin '%s' is being modified by another operation: %s",
                        entityId, conflictingOperation));
        this.entityId = entityId;
        this.expectedVersion = -1;
        this.actualVersion = -1;
        this.conflictingOperation = conflictingOperation;
    }

    public AdminConcurrencyException(String entityId, long expectedVersion,
                                     long actualVersion, CorrelationId correlationId) {
        super("OPTIMISTIC_LOCK_FAILURE",
                String.format("Optimistic lock failure for admin '%s': expected version %d, actual version %d",
                        entityId, expectedVersion, actualVersion),
                Severity.ERROR,
                correlationId);
        this.entityId = entityId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
        this.conflictingOperation = null;
    }

}