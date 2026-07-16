package biz.ugur.busroutebackend.catalogsearch.infrastructure.messaging;

import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.RebuildStats;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchCache;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.place.domain.events.GeoCatalogBulkChangedEvent;
import biz.ugur.busroutebackend.place.domain.events.PlaceCatalogChangedEvent;
import biz.ugur.busroutebackend.place.domain.events.StreetCatalogChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class CatalogSearchGeoReindexHandler {

    private static final Duration REINDEX_TIMEOUT = Duration.ofSeconds(15);

    private final CatalogSearchIndexRepository indexRepository;
    private final CatalogSearchCache cache;

    public CatalogSearchGeoReindexHandler(CatalogSearchIndexRepository indexRepository,
                                          CatalogSearchCache cache) {
        this.indexRepository = indexRepository;
        this.cache = cache;
    }

    @EventListener
    public void onPlaceChanged(PlaceCatalogChangedEvent event) {
        reindex(indexRepository.rebuildObject(CatalogObjectKind.PLACE, event.placeId()),
                "PLACE " + event.placeId());
    }

    @EventListener
    public void onStreetChanged(StreetCatalogChangedEvent event) {
        reindex(indexRepository.rebuildObject(CatalogObjectKind.STREET, event.streetId()),
                "STREET " + event.streetId());
    }

    @EventListener
    public void onBulkChanged(GeoCatalogBulkChangedEvent event) {
        reindex(indexRepository.rebuildAll(), "GEO BULK");
    }

    private void reindex(Mono<RebuildStats> rebuild, String target) {
        rebuild.flatMap(stats -> cache.evictAll().thenReturn(stats))
                .timeout(REINDEX_TIMEOUT)
                .doOnSuccess(stats -> log.info("[CATALOG_SEARCH] reindexed {}: {} rows", target, stats.inserted()))
                .doOnError(err -> log.error("[CATALOG_SEARCH] reindex failed for {}", target, err))
                .onErrorResume(err -> Mono.empty())
                .subscribe();
    }
}
