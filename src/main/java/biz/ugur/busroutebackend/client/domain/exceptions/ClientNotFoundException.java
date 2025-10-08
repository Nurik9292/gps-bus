package biz.ugur.busroutebackend.client.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;


@Getter
public class ClientNotFoundException extends ClientDomainException {

    private final String identifier;
    private final String identifierType;

    public ClientNotFoundException(String identifier, String identifierType) {
        super("NOT_FOUND", String.format("Client not found with %s: %s", identifierType, identifier), Severity.WARNING);
        this.identifier = identifier;
        this.identifierType = identifierType;
    }

    public ClientNotFoundException(String identifier, String identifierType, CorrelationId correlationId) {
        super("NOT_FOUND", String.format("Client not found with %s: %s", identifierType, identifier), Severity.WARNING, correlationId);
        this.identifier = identifier;
        this.identifierType = identifierType;
    }

    public static ClientNotFoundException byId(Long id) {
        return new ClientNotFoundException(id.toString(), "id");
    }

    public static ClientNotFoundException byId(Long id, CorrelationId correlationId) {
        return new ClientNotFoundException(id.toString(), "id", correlationId);
    }

    public static ClientNotFoundException byEmail(String email) {
        return new ClientNotFoundException(email, "email");
    }

    public static ClientNotFoundException byEmail(String email, CorrelationId correlationId) {
        return new ClientNotFoundException(email, "email", correlationId);
    }

    public static ClientNotFoundException byUsername(String username) {
        return new ClientNotFoundException(username, "username");
    }

    public static ClientNotFoundException byUsername(String username, CorrelationId correlationId) {
        return new ClientNotFoundException(username, "username", correlationId);
    }
}
