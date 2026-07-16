package biz.ugur.busroutebackend.catalogsearch.domain.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
public class SearchAlias {

    private final Long id;
    private final CatalogObjectKind objectKind;
    private final String objectId;
    private final String aliasRaw;
    private final String aliasNorm;
    private final BigDecimal weight;
    private final AliasSource source;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;

    public static SearchAlias create(CatalogObjectKind objectKind, String objectId,
                                     String aliasRaw, String aliasNorm,
                                     BigDecimal weight, AliasSource source, String createdBy) {
        Instant now = Instant.now();
        return SearchAlias.builder()
                .objectKind(objectKind)
                .objectId(objectId)
                .aliasRaw(aliasRaw)
                .aliasNorm(aliasNorm)
                .weight(weight)
                .source(source)
                .createdBy(createdBy)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public SearchAlias withCuration(BigDecimal newWeight, AliasSource newSource) {
        return toBuilder()
                .weight(newWeight != null ? newWeight : weight)
                .source(newSource != null ? newSource : source)
                .updatedAt(Instant.now())
                .build();
    }
}
