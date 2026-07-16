package biz.ugur.busroutebackend.catalogsearch.application.dto;

import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogName;

import java.math.BigDecimal;
import java.time.Instant;

public record CatalogNameResult(String objectKind, String aliasId, String objectId,
                                String objectTitle, String aliasRaw, String aliasNorm,
                                BigDecimal weight, String source, Instant updatedAt) {

    public static CatalogNameResult fromDomain(CatalogName name) {
        return new CatalogNameResult(name.objectKind().name(), name.aliasId(), name.objectId(),
                name.objectTitle(), name.aliasRaw(), name.aliasNorm(),
                name.weight(), name.source(), name.updatedAt());
    }
}
