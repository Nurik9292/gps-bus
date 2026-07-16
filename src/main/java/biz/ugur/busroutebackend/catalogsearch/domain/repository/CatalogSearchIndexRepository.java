package biz.ugur.busroutebackend.catalogsearch.domain.repository;

import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.RebuildStats;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchHit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CatalogSearchIndexRepository {

    Mono<RebuildStats> rebuildAll();

    Mono<RebuildStats> rebuildObject(CatalogObjectKind kind, String objectId);

    Flux<SearchHit> search(String normalizedQuery, int limit);
}
