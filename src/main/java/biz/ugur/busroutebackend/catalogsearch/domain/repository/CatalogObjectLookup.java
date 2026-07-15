package biz.ugur.busroutebackend.catalogsearch.domain.repository;

import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import reactor.core.publisher.Mono;

public interface CatalogObjectLookup {

    Mono<String> findTitle(CatalogObjectKind kind, String objectId);
}
