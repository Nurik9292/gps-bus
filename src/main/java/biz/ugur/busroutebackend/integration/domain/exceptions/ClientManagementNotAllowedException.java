package biz.ugur.busroutebackend.integration.domain.exceptions;


import lombok.Getter;

@Getter
public class ClientManagementNotAllowedException extends ExternalServiceException {

    private final String serviceId;

    public ClientManagementNotAllowedException(String serviceId) {
        super(
                "CLIENT_MANAGEMENT.FORBIDDEN",
                "Service is not configured to manage clients. Create service with canManageClients=true to enable this feature.",
                Severity.WARNING
        );
        this.serviceId = serviceId;
    }

}
