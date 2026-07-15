package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogSearchValidationException;
import biz.ugur.busroutebackend.catalogsearch.domain.model.AliasSource;
import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;

import java.math.BigDecimal;

final class AliasCommandValidator {

    static final int ALIAS_MAX_LENGTH = 300;
    static final BigDecimal WEIGHT_MIN = new BigDecimal("0.1");
    static final BigDecimal WEIGHT_MAX = new BigDecimal("3.0");
    static final BigDecimal WEIGHT_DEFAULT = BigDecimal.ONE;

    private AliasCommandValidator() {
    }

    static CatalogObjectKind requireKind(String raw) {
        CatalogObjectKind kind = raw == null ? null : CatalogObjectKind.fromString(raw);
        if (kind == null) {
            throw new CatalogSearchValidationException("INVALID_OBJECT_KIND",
                    "objectKind must be one of STOP, ROUTE: " + raw);
        }
        return kind;
    }

    static String requireObjectId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CatalogSearchValidationException("OBJECT_ID_BLANK", "objectId must not be blank");
        }
        return raw;
    }

    static String requireAliasRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CatalogSearchValidationException("ALIAS_BLANK", "aliasRaw must not be blank");
        }
        if (raw.length() > ALIAS_MAX_LENGTH) {
            throw new CatalogSearchValidationException("ALIAS_TOO_LONG",
                    "aliasRaw exceeds " + ALIAS_MAX_LENGTH + " characters");
        }
        return raw;
    }

    static BigDecimal requireWeight(BigDecimal raw) {
        BigDecimal weight = raw == null ? WEIGHT_DEFAULT : raw;
        if (weight.compareTo(WEIGHT_MIN) < 0 || weight.compareTo(WEIGHT_MAX) > 0) {
            throw new CatalogSearchValidationException("WEIGHT_OUT_OF_RANGE",
                    "weight must be within [0.1, 3.0]: " + weight);
        }
        return weight;
    }

    static AliasSource requireApiSource(String raw, AliasSource fallback) {
        if (raw == null) {
            return fallback;
        }
        AliasSource source = AliasSource.fromString(raw);
        if (source == null || !source.apiAllowed()) {
            throw new CatalogSearchValidationException("INVALID_SOURCE",
                    "source must be one of CURATED, COLLOQUIAL, POI: " + raw);
        }
        return source;
    }
}
