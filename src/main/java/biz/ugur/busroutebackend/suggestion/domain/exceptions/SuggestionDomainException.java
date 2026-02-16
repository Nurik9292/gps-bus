package biz.ugur.busroutebackend.suggestion.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;

public class SuggestionDomainException extends AbstractDomainException {

    protected SuggestionDomainException(String errorCode, String message) {
        super(errorCode, message);
    }

    protected SuggestionDomainException(String errorCode, String message, Severity severity) {
        super(errorCode, message, severity);
    }
}
