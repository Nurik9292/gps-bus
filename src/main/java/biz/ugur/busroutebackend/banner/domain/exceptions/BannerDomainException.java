package biz.ugur.busroutebackend.banner.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;

public abstract class BannerDomainException extends AbstractDomainException {

    protected BannerDomainException(String errorCode, String message) {
        super("BANNER." + errorCode, message);
    }

    protected BannerDomainException(String errorCode, String message, Severity severity) {
        super("BANNER." + errorCode, message, severity);
    }

    protected BannerDomainException(String errorCode, String message, Severity severity, CorrelationId correlationId) {
        super("BANNER." + errorCode, message, severity, correlationId);
    }
}
