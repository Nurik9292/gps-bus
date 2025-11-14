package biz.ugur.busroutebackend.integration.domain.exceptions;

import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;


public class ExternalServiceBlockedException extends ExternalServiceException {

    public ExternalServiceBlockedException(ExternalServiceId id) {
        super("External service is blocked: " + id.getValue());
    }

    public ExternalServiceBlockedException(String serviceName) {
        super("External service is blocked: " + serviceName);
    }
}
