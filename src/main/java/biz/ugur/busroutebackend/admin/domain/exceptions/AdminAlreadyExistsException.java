package biz.ugur.busroutebackend.admin.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.CorrelationId;
import lombok.Getter;

@Getter
public class AdminAlreadyExistsException extends AdminDomainException {

    private final String username;

    public AdminAlreadyExistsException(String username) {
        super("ALREADY_EXISTS",
                String.format("Admin with username '%s' already exists", username));
        this.username = username;
    }

    public AdminAlreadyExistsException(String username, CorrelationId correlationId) {
        super("ALREADY_EXISTS",
                String.format("Admin with username '%s' already exists", username),
                Severity.ERROR,
                correlationId);
        this.username = username;
    }

}
