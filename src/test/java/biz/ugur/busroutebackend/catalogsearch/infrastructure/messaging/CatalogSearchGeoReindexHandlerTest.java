package biz.ugur.busroutebackend.catalogsearch.infrastructure.messaging;

import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.RebuildStats;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchCache;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.place.domain.events.GeoCatalogBulkChangedEvent;
import biz.ugur.busroutebackend.place.domain.events.PlaceCatalogChangedEvent;
import biz.ugur.busroutebackend.place.domain.events.StreetCatalogChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogSearchGeoReindexHandlerTest {

    @InjectMocks
    private CatalogSearchGeoReindexHandler handler;

    @Mock
    private CatalogSearchIndexRepository indexRepository;

    @Mock
    private CatalogSearchCache cache;

    @Test
    void placeChangeTriggersPointwisePlaceRebuildAndCacheEviction() {
        when(cache.evictAll()).thenReturn(Mono.just(0L));
        when(indexRepository.rebuildObject(CatalogObjectKind.PLACE, "place-1"))
                .thenReturn(Mono.just(new RebuildStats(3, 0)));

        handler.onPlaceChanged(new PlaceCatalogChangedEvent("place-1"));

        verify(indexRepository).rebuildObject(CatalogObjectKind.PLACE, "place-1");
        verify(cache).evictAll();
        verify(indexRepository, never()).rebuildAll();
    }

    @Test
    void streetChangeTriggersPointwiseStreetRebuildAndCacheEviction() {
        when(cache.evictAll()).thenReturn(Mono.just(0L));
        when(indexRepository.rebuildObject(CatalogObjectKind.STREET, "street-1"))
                .thenReturn(Mono.just(new RebuildStats(2, 0)));

        handler.onStreetChanged(new StreetCatalogChangedEvent("street-1"));

        verify(indexRepository).rebuildObject(CatalogObjectKind.STREET, "street-1");
        verify(cache).evictAll();
    }

    @Test
    void bulkChangeTriggersFullRebuildAndCacheEviction() {
        when(cache.evictAll()).thenReturn(Mono.just(0L));
        when(indexRepository.rebuildAll()).thenReturn(Mono.just(new RebuildStats(100, 0)));

        handler.onBulkChanged(new GeoCatalogBulkChangedEvent());

        verify(indexRepository).rebuildAll();
        verify(cache).evictAll();
    }

    @Test
    void rebuildFailureIsSwallowedWithoutThrowing() {
        when(indexRepository.rebuildObject(CatalogObjectKind.PLACE, "place-broken"))
                .thenReturn(Mono.error(new IllegalStateException("db down")));

        handler.onPlaceChanged(new PlaceCatalogChangedEvent("place-broken"));

        verify(indexRepository).rebuildObject(CatalogObjectKind.PLACE, "place-broken");
        verify(cache, never()).evictAll();
    }
}
