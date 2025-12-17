package biz.ugur.busroutebackend.integration.domain.exceptions;

import lombok.Getter;


@Getter
public class ClientNotBelongsToServiceException extends ExternalServiceException {

    private final String clientId;
    private final String serviceId;

    public ClientNotBelongsToServiceException(String clientId, String serviceId) {
        super(
                "CLIENT_ACCESS_DENIED",
                "Client " + clientId + " does not belong to service " + serviceId,
                Severity.WARNING
        );
        this.clientId = clientId;
        this.serviceId = serviceId;
    }
}
