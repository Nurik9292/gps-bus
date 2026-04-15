package biz.ugur.busroutebackend.advertising.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;

public abstract class AdvertisingDomainException extends AbstractDomainException {

    protected AdvertisingDomainException(String errorCode, String message) {
        super("ADVERTISING." + errorCode, message);
    }

    protected AdvertisingDomainException(String errorCode, String message, Severity severity) {
        super("ADVERTISING." + errorCode, message, severity);
    }
}
