package biz.ugur.busroutebackend.admin.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.CorrelationId;
import lombok.Getter;

@Getter
public class AdminNotFoundException extends AdminDomainException {
    private final String identifier;
    private final String identifierType;

    public AdminNotFoundException(String username) {
        super("NOT_FOUND",
                String.format("Admin with username '%s' not found", username));
        this.identifier = username;
        this.identifierType = "username";
    }

    public AdminNotFoundException(String identifier, String identifierType) {
        super("NOT_FOUND",
                String.format("Admin with %s '%s' not found", identifierType, identifier));
        this.identifier = identifier;
        this.identifierType = identifierType;
    }

    public AdminNotFoundException(String identifier, String identifierType, CorrelationId correlationId) {
        super("NOT_FOUND",
                String.format("Admin with %s '%s' not found", identifierType, identifier),
                Severity.ERROR,
                correlationId);
        this.identifier = identifier;
        this.identifierType = identifierType;
    }

}
