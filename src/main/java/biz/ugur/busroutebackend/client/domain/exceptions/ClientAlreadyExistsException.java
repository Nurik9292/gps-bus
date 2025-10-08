package biz.ugur.busroutebackend.client.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;


@Getter
public class ClientAlreadyExistsException extends ClientDomainException {

    private final String identifier;
    private final String identifierType;

    public ClientAlreadyExistsException(String identifier, String identifierType) {
        super("ALREADY_EXISTS", String.format("Client already exists with %s: %s", identifierType, identifier), Severity.WARNING);
        this.identifier = identifier;
        this.identifierType = identifierType;
    }

    public ClientAlreadyExistsException(String identifier, String identifierType, CorrelationId correlationId) {
        super("ALREADY_EXISTS", String.format("Client already exists with %s: %s", identifierType, identifier), Severity.WARNING, correlationId);
        this.identifier = identifier;
        this.identifierType = identifierType;
    }

    public static ClientAlreadyExistsException byEmail(String email) {
        return new ClientAlreadyExistsException(email, "email");
    }

    public static ClientAlreadyExistsException byEmail(String email, CorrelationId correlationId) {
        return new ClientAlreadyExistsException(email, "email", correlationId);
    }

    public static ClientAlreadyExistsException byUsername(String username) {
        return new ClientAlreadyExistsException(username, "username");
    }

    public static ClientAlreadyExistsException byUsername(String username, CorrelationId correlationId) {
        return new ClientAlreadyExistsException(username, "username", correlationId);
    }
}
