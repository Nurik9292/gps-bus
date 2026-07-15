package biz.ugur.busroutebackend.catalogsearch.domain.repository;

import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchHit;
import reactor.core.publisher.Mono;

import java.util.List;

public interface CatalogSearchCache {

    Mono<List<SearchHit>> get(String normalizedQuery, int limit);

    Mono<Void> put(String normalizedQuery, int limit, List<SearchHit> hits);

    Mono<Long> evictAll();
}
