package biz.ugur.busroutebackend.catalogsearch.application.dto;

import java.math.BigDecimal;

public record CreateAliasCommand(String objectKind, String objectId, String aliasRaw,
                                 BigDecimal weight, String source) {
}
