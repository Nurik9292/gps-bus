package biz.ugur.busroutebackend.integration.domain.exceptions;

import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;


public class ExternalServiceNotFoundException extends ExternalServiceException {

    public ExternalServiceNotFoundException(ExternalServiceId id) {
        super("External service not found: " + id.getValue());
    }

    public ExternalServiceNotFoundException(String apiToken) {
        super("External service not found for token: " + maskToken(apiToken));
    }

    private static String maskToken(String token) {
        if (token == null || token.length() <= 12) {
            return "****";
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 4);
    }
}
