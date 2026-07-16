package biz.ugur.busroutebackend.catalogsearch.application.dto;

import java.math.BigDecimal;

public record PatchAliasCommand(Long aliasId, BigDecimal weight, String source, boolean aliasRawPresent) {
}
