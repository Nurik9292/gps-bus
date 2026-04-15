package biz.ugur.busroutebackend.payment.domain.enums;

import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentValidationException;
import lombok.Getter;

import java.util.Arrays;

/**
 * External payment provider. All sv_epg-based banks (Rysgal, Senagat) share the same
 * integration protocol — differ only in credentials and host.
 */
@Getter
public enum PaymentProvider {
    RYSGAL("sv_epg"),
    SENAGAT("sv_epg"),
    BKB("sv_epg"),
    HALK("sv_epg");

    /** Underlying protocol implementation key. Used to select the provider strategy. */
    private final String protocol;

    PaymentProvider(String protocol) {
        this.protocol = protocol;
    }

    public static PaymentProvider from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new PaymentValidationException("provider", "must not be null");
        }
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseThrow(() -> new PaymentValidationException(
                        "provider", "unknown provider: " + raw));
    }
}
