package biz.ugur.busroutebackend.payment.domain.enums;

import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentValidationException;
import lombok.Getter;

import java.util.Arrays;


@Getter
public enum PaymentProvider {
    RYSGAL("sv_epg"),
    SENAGAT("sv_epg"),
    BKB("sv_epg"),
    HALK("sv_epg"),
    CASH("cash");

    private final String protocol;

    PaymentProvider(String protocol) {
        this.protocol = protocol;
    }

    public boolean isElectronic() {
        return "sv_epg".equals(protocol);
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
