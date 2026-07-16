package biz.ugur.busroutebackend.catalogsearch.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record CatalogName(CatalogObjectKind objectKind, String aliasId, String objectId,
                          String objectTitle, String aliasRaw, String aliasNorm,
                          BigDecimal weight, String source, Instant updatedAt) {
}
