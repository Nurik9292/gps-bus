package biz.ugur.busroutebackend.catalogsearch.domain.model;

public record AliasCollision(Long aliasId, CatalogObjectKind objectKind, String objectId, String title) {
}
