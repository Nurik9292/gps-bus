package biz.ugur.busroutebackend.catalogsearch.domain.repository;

import biz.ugur.busroutebackend.catalogsearch.domain.model.AliasCollision;
import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogName;
import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchAlias;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchAliasView;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SearchAliasRepository {

    Mono<SearchAlias> save(SearchAlias alias);

    Mono<SearchAlias> findById(Long id);

    Mono<Void> deleteById(Long id);

    Flux<SearchAliasView> findByObject(CatalogObjectKind kind, String objectId);

    Flux<SearchAliasView> searchByText(String query, int page, int size);

    Mono<Long> countByText(String query);

    Flux<CatalogName> searchNames(String query, int page, int size);

    Mono<Long> countNames(String query);

    Mono<Boolean> existsByObjectAndRaw(CatalogObjectKind kind, String objectId, String aliasRaw);

    Flux<AliasCollision> findCollisions(String aliasNorm, CatalogObjectKind excludeKind, String excludeObjectId);

    Mono<String> normalize(String raw);
}
