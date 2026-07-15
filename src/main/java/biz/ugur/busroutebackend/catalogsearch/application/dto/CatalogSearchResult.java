package biz.ugur.busroutebackend.catalogsearch.application.dto;

import java.util.List;

public record CatalogSearchResult(String query, boolean transitOnly, List<SearchHitResult> items) {
}
