package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogSearchValidationException;
import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchHit;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchCache;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
import biz.ugur.busroutebackend.catalogsearch.infrastructure.config.CatalogSearchProperties;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchResult;
import biz.ugur.busroutebackend.place.application.usecase.GeocodeFallbackUseCase;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchCatalogUseCaseTest {

    private static final SearchHit STOP_HIT = new SearchHit(CatalogObjectKind.STOP, "stop-1",
            "Berkarar SM", null, 37.95, 58.38, 2.4, "CURATED");
    private static final SearchHit PLACE_HIT = new SearchHit(CatalogObjectKind.PLACE, "place-1",
            "Berkarar söwda merkezi", "Söwda merkezi · Aşgabat", 37.94, 58.37, 0.9, "NAME");
    private static final PlaceSearchResult GEOCODED = new PlaceSearchResult(null,
            "Berkarar, Ak bugdaý etraby", null, null, null, "Ahal welaýaty", null,
            new BigDecimal("37.90"), new BigDecimal("58.30"), "Ahal", List.of(), 0.3);

    @Mock
    private CatalogSearchIndexRepository indexRepository;
    @Mock
    private SearchAliasRepository aliasRepository;
    @Mock
    private CatalogSearchCache cache;
    @Mock
    private GeocodeFallbackUseCase geocodeFallbackUseCase;
    @Mock
    private CorrelationContextService correlationService;
    @Mock
    private EventBus eventBus;

    private CatalogSearchProperties properties;
    private SearchCatalogUseCase useCase;

    @BeforeEach
    void setUp() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        properties = new CatalogSearchProperties();
        useCase = new SearchCatalogUseCase(indexRepository, aliasRepository, cache,
                geocodeFallbackUseCase, properties, correlationService, eventBus);
    }

    private void stubIndexed(SearchHit... hits) {
        when(aliasRepository.normalize("berkar")).thenReturn(Mono.just("berkar"));
        when(cache.get("berkar", 10)).thenReturn(Mono.empty());
        when(indexRepository.search("berkar", 10)).thenReturn(Flux.just(hits));
        when(cache.put(anyString(), anyInt(), any())).thenReturn(Mono.empty());
    }

    @Test
    void gate17FederatedResultContainsIndexedPlaceAndStopTogether() {
        stubIndexed(STOP_HIT, PLACE_HIT);
        when(geocodeFallbackUseCase.execute(any(Mono.class))).thenReturn(Mono.just(List.of()));

        StepVerifier.create(useCase.execute(Mono.just(new SearchCatalogUseCase.Query("berkar", null, null, null))))
                .assertNext(result -> {
                    assertThat(result.transitOnly()).isFalse();
                    assertThat(result.items()).extracting("objectKind")
                            .containsExactlyInAnyOrder("STOP", "PLACE");
                    assertThat(result.items()).extracting("objectId")
                            .containsExactlyInAnyOrder("stop-1", "place-1");
                })
                .verifyComplete();
    }

    @Test
    void geocodeFallbackFiresOnlyBelowThreshold() {
        stubIndexed(STOP_HIT);
        when(geocodeFallbackUseCase.execute(any(Mono.class))).thenReturn(Mono.just(List.of(GEOCODED)));

        StepVerifier.create(useCase.execute(Mono.just(new SearchCatalogUseCase.Query("berkar", null, null, null))))
                .assertNext(result -> {
                    assertThat(result.items()).hasSize(2);
                    assertThat(result.items()).extracting("objectKind")
                            .containsExactlyInAnyOrder("STOP", "PLACE");
                    assertThat(result.items()).extracting("objectId")
                            .containsExactlyInAnyOrder("stop-1", null);
                })
                .verifyComplete();
    }

    @Test
    void geocodeFallbackSkippedWhenIndexYieldsEnoughHits() {
        SearchHit second = new SearchHit(CatalogObjectKind.ROUTE, "route-1", "142", null, null, null, 1.2, "NAME");
        SearchHit third = new SearchHit(CatalogObjectKind.STREET, "street-1", "Görogly köçesi", null, null, null, 1.0, "NAME");
        stubIndexed(STOP_HIT, second, third);

        StepVerifier.create(useCase.execute(Mono.just(new SearchCatalogUseCase.Query("berkar", null, null, null))))
                .assertNext(result -> assertThat(result.items()).hasSize(3))
                .verifyComplete();
        verify(geocodeFallbackUseCase, never()).execute(any(Mono.class));
    }

    @Test
    void gate18GeocodeFailureDegradesToIndexedItems() {
        stubIndexed(STOP_HIT);
        when(geocodeFallbackUseCase.execute(any(Mono.class)))
                .thenReturn(Mono.error(new IllegalStateException("nominatim down")));

        StepVerifier.create(useCase.execute(Mono.just(new SearchCatalogUseCase.Query("berkar", null, null, null))))
                .assertNext(result -> {
                    assertThat(result.items()).hasSize(1);
                    assertThat(result.items().get(0).objectKind()).isEqualTo("STOP");
                })
                .verifyComplete();
    }

    @Test
    void geoBoostPrefersNearbyObjectOnEqualScore() {
        SearchHit near = new SearchHit(CatalogObjectKind.STOP, "stop-near", "AAA near",
                null, 37.9500, 58.3800, 1.0, "NAME");
        SearchHit far = new SearchHit(CatalogObjectKind.STOP, "stop-far", "AAA far",
                null, 38.9500, 58.3800, 1.0, "NAME");
        stubIndexed(far, near);
        when(geocodeFallbackUseCase.execute(any(Mono.class))).thenReturn(Mono.just(List.of()));

        StepVerifier.create(useCase.execute(Mono.just(
                        new SearchCatalogUseCase.Query("berkar", null, 37.9501, 58.3801))))
                .assertNext(result -> assertThat(result.items().get(0).objectId()).isEqualTo("stop-near"))
                .verifyComplete();
    }

    @Test
    void federationDisabledKeepsTransitOnlyContractAndSkipsGeocode() {
        properties.setFederationEnabled(false);
        stubIndexed(STOP_HIT);

        StepVerifier.create(useCase.execute(Mono.just(new SearchCatalogUseCase.Query("berkar", null, null, null))))
                .assertNext(result -> {
                    assertThat(result.transitOnly()).isTrue();
                    assertThat(result.items()).hasSize(1);
                })
                .verifyComplete();
        verify(geocodeFallbackUseCase, never()).execute(any(Mono.class));
    }

    @Test
    void cacheHitSkipsIndexQueryButStillFederates() {
        when(aliasRepository.normalize("berkar")).thenReturn(Mono.just("berkar"));
        when(cache.get("berkar", 10)).thenReturn(Mono.just(List.of(STOP_HIT)));
        when(geocodeFallbackUseCase.execute(any(Mono.class))).thenReturn(Mono.just(List.of(GEOCODED)));

        StepVerifier.create(useCase.execute(Mono.just(new SearchCatalogUseCase.Query("berkar", null, null, null))))
                .assertNext(result -> assertThat(result.items()).hasSize(2))
                .verifyComplete();
        verify(indexRepository, never()).search(anyString(), anyInt());
    }

    @Test
    void emptyNormalizedQueryReturnsEmptyWithoutTouchingAnything() {
        when(aliasRepository.normalize("!!!")).thenReturn(Mono.just(""));

        StepVerifier.create(useCase.execute(Mono.just(new SearchCatalogUseCase.Query("!!!", null, null, null))))
                .assertNext(result -> assertThat(result.items()).isEmpty())
                .verifyComplete();
        verify(cache, never()).get(anyString(), anyInt());
        verify(geocodeFallbackUseCase, never()).execute(any(Mono.class));
    }

    @Test
    void limitOutOfRangeRejected() {
        StepVerifier.create(useCase.execute(Mono.just(new SearchCatalogUseCase.Query("q", 51, null, null))))
                .expectErrorSatisfies(err -> {
                    CatalogSearchValidationException ex =
                            assertInstanceOf(CatalogSearchValidationException.class, err);
                    assertThat(ex.getBusinessCode()).isEqualTo("LIMIT_OUT_OF_RANGE");
                })
                .verify();
    }
}
