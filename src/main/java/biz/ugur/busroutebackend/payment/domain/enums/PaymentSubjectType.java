package biz.ugur.busroutebackend.payment.domain.enums;

import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentValidationException;

import java.util.Arrays;

/** What a payment pays for. Kept explicit to avoid typos and to make billing reports unambiguous. */
public enum PaymentSubjectType {

    AD_PLACEMENT;

    public static PaymentSubjectType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new PaymentValidationException("subjectType", "must not be null");
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseThrow(() -> new PaymentValidationException(
                        "subjectType", "unknown subject type: " + raw));
    }
}
