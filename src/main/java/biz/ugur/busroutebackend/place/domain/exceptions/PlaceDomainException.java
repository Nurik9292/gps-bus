package biz.ugur.busroutebackend.place.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;

public class PlaceDomainException extends AbstractDomainException {

    protected PlaceDomainException(String errorCode, String message) {
        super(errorCode, message);
    }

    protected PlaceDomainException(String errorCode, String message, Severity severity) {
        super(errorCode, message, severity);
    }
}
