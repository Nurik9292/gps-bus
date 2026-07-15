package biz.ugur.busroutebackend.catalogsearch.application.dto;

import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchAlias;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchAliasView;

import java.math.BigDecimal;
import java.time.Instant;

public record AliasResult(Long id, String objectKind, String objectId, String objectTitle,
                          String aliasRaw, String aliasNorm, BigDecimal weight, String source,
                          String createdBy, Instant createdAt, Instant updatedAt) {

    public static AliasResult fromDomain(SearchAliasView view) {
        return fromDomain(view.alias(), view.objectTitle());
    }

    public static AliasResult fromDomain(SearchAlias alias, String objectTitle) {
        return new AliasResult(alias.getId(), alias.getObjectKind().name(), alias.getObjectId(),
                objectTitle, alias.getAliasRaw(), alias.getAliasNorm(), alias.getWeight(),
                alias.getSource().name(), alias.getCreatedBy(), alias.getCreatedAt(), alias.getUpdatedAt());
    }
}
