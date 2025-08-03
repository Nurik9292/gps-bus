package biz.ugur.busroutebackend.admin.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.CorrelationId;
import lombok.Getter;

@Getter
public class AdminDeleteException extends AdminDomainException {

    private String adminId;

    public AdminDeleteException(String errorCode, String message, String adminId, CorrelationId correlationId) {
        this(errorCode, message, correlationId);
        this.adminId = adminId;
    }

    protected AdminDeleteException(String errorCode, String message, CorrelationId correlationId) {
        super(errorCode, message, Severity.ERROR, correlationId);
    }
}
