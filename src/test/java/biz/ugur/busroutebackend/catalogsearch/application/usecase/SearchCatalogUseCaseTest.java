package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogSearchValidationException;
import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchHit;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchCache;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
import biz.ugur.busroutebackend.catalogsearch.infrastructure.config.CatalogSearchProperties;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchResult;
import biz.ugur.busroutebackend.place.application.usecase.SearchPlacesUseCase;
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
    private static final PlaceSearchResult PLACE = new PlaceSearchResult("place-1",
            "Berkarar söwda merkezi", null, null, null, "Aşgabat", "Söwda merkezi",
            new BigDecimal("37.94"), new BigDecimal("58.37"), "city-1", List.of(), 0.9);

    @Mock
    private CatalogSearchIndexRepository indexRepository;
    @Mock
    private SearchAliasRepository aliasRepository;
    @Mock
    private CatalogSearchCache cache;
    @Mock
    private SearchPlacesUseCase searchPlacesUseCase;
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
                searchPlacesUseCase, properties, correlationService, eventBus);
    }

    private void stubTransit(SearchHit... hits) {
        when(aliasRepository.normalize("berkar")).thenReturn(Mono.just("berkar"));
        when(cache.get("berkar", 10)).thenReturn(Mono.empty());
        when(indexRepository.search("berkar", 10)).thenReturn(Flux.just(hits));
        when(cache.put(anyString(), anyInt(), any())).thenReturn(Mono.empty());
    }

    @Test
    void gate17FederatedResultContainsBothStopAndPlace() {
        stubTransit(STOP_HIT);
        when(searchPlacesUseCase.execute(any(Mono.class))).thenReturn(Mono.just(List.of(PLACE)));

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
    void gate18PlaceFailureDegradesToTransitOnlyItems() {
        stubTransit(STOP_HIT);
        when(searchPlacesUseCase.execute(any(Mono.class)))
                .thenReturn(Mono.error(new IllegalStateException("place down")));

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
        stubTransit(far, near);
        when(searchPlacesUseCase.execute(any(Mono.class))).thenReturn(Mono.just(List.of()));

        StepVerifier.create(useCase.execute(Mono.just(
                        new SearchCatalogUseCase.Query("berkar", null, 37.9501, 58.3801))))
                .assertNext(result -> assertThat(result.items().get(0).objectId()).isEqualTo("stop-near"))
                .verifyComplete();
    }

    @Test
    void federationDisabledKeepsTransitOnlyContractAndSkipsPlaces() {
        properties.setFederationEnabled(false);
        stubTransit(STOP_HIT);

        StepVerifier.create(useCase.execute(Mono.just(new SearchCatalogUseCase.Query("berkar", null, null, null))))
                .assertNext(result -> {
                    assertThat(result.transitOnly()).isTrue();
                    assertThat(result.items()).hasSize(1);
                })
                .verifyComplete();
        verify(searchPlacesUseCase, never()).execute(any(Mono.class));
    }

    @Test
    void cacheHitSkipsIndexQueryButStillFederates() {
        when(aliasRepository.normalize("berkar")).thenReturn(Mono.just("berkar"));
        when(cache.get("berkar", 10)).thenReturn(Mono.just(List.of(STOP_HIT)));
        when(searchPlacesUseCase.execute(any(Mono.class))).thenReturn(Mono.just(List.of(PLACE)));

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
        verify(searchPlacesUseCase, never()).execute(any(Mono.class));
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
