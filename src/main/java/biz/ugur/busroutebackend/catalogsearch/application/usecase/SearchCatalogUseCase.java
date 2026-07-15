package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.application.dto.CatalogSearchResult;
import biz.ugur.busroutebackend.catalogsearch.application.dto.SearchHitResult;
import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogSearchValidationException;
import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchHit;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchCache;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
import biz.ugur.busroutebackend.catalogsearch.infrastructure.config.CatalogSearchProperties;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchQuery;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchResult;
import biz.ugur.busroutebackend.place.application.usecase.SearchPlacesUseCase;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class SearchCatalogUseCase extends BaseUseCase<Mono<SearchCatalogUseCase.Query>, CatalogSearchResult> {

    public record Query(String q, Integer limit, Double lat, Double lon) {
    }

    static final int LIMIT_DEFAULT = 10;
    static final int LIMIT_MAX = 50;

    private final CatalogSearchIndexRepository indexRepository;
    private final SearchAliasRepository aliasRepository;
    private final CatalogSearchCache cache;
    private final SearchPlacesUseCase searchPlacesUseCase;
    private final CatalogSearchProperties properties;

    public SearchCatalogUseCase(CatalogSearchIndexRepository indexRepository,
                                SearchAliasRepository aliasRepository,
                                CatalogSearchCache cache,
                                SearchPlacesUseCase searchPlacesUseCase,
                                CatalogSearchProperties properties,
                                CorrelationContextService correlationService,
                                EventBus eventBus) {
        super(correlationService, eventBus);
        this.indexRepository = indexRepository;
        this.aliasRepository = aliasRepository;
        this.cache = cache;
        this.searchPlacesUseCase = searchPlacesUseCase;
        this.properties = properties;
    }

    @Override
    protected Mono<CatalogSearchResult> process(Mono<Query> request) {
        return request.flatMap(query -> {
            int limit = resolveLimit(query.limit());
            String q = query.q() == null ? "" : query.q();
            boolean federated = properties.isFederationEnabled();
            return aliasRepository.normalize(q)
                    .flatMap(qn -> qn.isBlank()
                            ? Mono.just(new CatalogSearchResult(q, !federated, List.of()))
                            : federatedSearch(q, qn, limit, query.lat(), query.lon(), federated));
        });
    }

    private Mono<CatalogSearchResult> federatedSearch(String q, String qn, int limit,
                                                      Double lat, Double lon, boolean federated) {
        Mono<List<SearchHit>> transit = cache.get(qn, limit)
                .switchIfEmpty(Mono.defer(() -> indexRepository.search(qn, limit)
                        .collectList()
                        .flatMap(hits -> cache.put(qn, limit, hits).thenReturn(hits))));
        if (!federated) {
            return transit.map(hits -> toResult(q, true, rank(hits, lat, lon, limit)));
        }
        Mono<List<SearchHit>> places = searchPlacesUseCase
                .execute(Mono.just(new PlaceSearchQuery(q, null, null, limit, null)))
                .timeout(Duration.ofMillis(properties.getPlaceTimeoutMs()))
                .map(this::toPlaceHits)
                .onErrorResume(err -> {
                    log.warn("[CATALOG_SEARCH] place federation degraded: {}", err.getMessage());
                    return Mono.just(List.of());
                });
        return Mono.zip(transit, places)
                .map(tuple -> {
                    List<SearchHit> merged = new ArrayList<>(tuple.getT1());
                    merged.addAll(tuple.getT2());
                    return toResult(q, false, rank(merged, lat, lon, limit));
                });
    }

    private List<SearchHit> toPlaceHits(List<PlaceSearchResult> places) {
        return places.stream()
                .map(p -> new SearchHit(CatalogObjectKind.PLACE, p.id(), p.name(),
                        subtitleOf(p),
                        p.latitude() == null ? null : p.latitude().doubleValue(),
                        p.longitude() == null ? null : p.longitude().doubleValue(),
                        (p.similarityScore() == null ? 0.5 : p.similarityScore())
                                * properties.getPlaceScoreFactor(),
                        "PLACE"))
                .toList();
    }

    private static String subtitleOf(PlaceSearchResult place) {
        if (place.category() != null && place.address() != null) {
            return place.category() + " · " + place.address();
        }
        return place.category() != null ? place.category() : place.address();
    }

    private List<SearchHit> rank(List<SearchHit> hits, Double lat, Double lon, int limit) {
        List<SearchHit> boosted = (lat == null || lon == null) ? hits : hits.stream()
                .map(hit -> hit.lat() == null || hit.lon() == null ? hit
                        : new SearchHit(hit.objectKind(), hit.objectId(), hit.title(), hit.subtitle(),
                                hit.lat(), hit.lon(),
                                hit.score() + properties.getGeoBoostFactor()
                                        / (1.0 + distanceKm(lat, lon, hit.lat(), hit.lon())),
                                hit.source()))
                .toList();
        return boosted.stream()
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed()
                        .thenComparing(SearchHit::title))
                .limit(limit)
                .toList();
    }

    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private static CatalogSearchResult toResult(String q, boolean transitOnly, List<SearchHit> hits) {
        return new CatalogSearchResult(q, transitOnly,
                hits.stream().map(SearchHitResult::fromDomain).toList());
    }

    private int resolveLimit(Integer raw) {
        int limit = raw == null ? LIMIT_DEFAULT : raw;
        if (limit < 1 || limit > LIMIT_MAX) {
            throw new CatalogSearchValidationException("LIMIT_OUT_OF_RANGE",
                    "limit must be within [1, " + LIMIT_MAX + "]: " + limit);
        }
        return limit;
    }

    @Override
    protected String getBoundContext() {
        return "catalogsearch";
    }
}
