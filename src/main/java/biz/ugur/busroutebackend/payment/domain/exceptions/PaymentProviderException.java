package biz.ugur.busroutebackend.payment.domain.exceptions;

import lombok.Getter;

@Getter
public class PaymentProviderException extends PaymentDomainException {

    private final String providerErrorCode;

    public PaymentProviderException(String providerErrorCode, String message) {
        super("PROVIDER_ERROR", message, Severity.ERROR);
        this.providerErrorCode = providerErrorCode;
    }

    public PaymentProviderException(String message, Throwable cause) {
        super("PROVIDER_ERROR", message + ": " + cause.getMessage(), Severity.ERROR);
        this.providerErrorCode = null;
        initCause(cause);
    }
}
