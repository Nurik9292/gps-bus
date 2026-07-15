package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogSearchValidationException;
import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchHit;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchCache;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
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

    private static final SearchHit HIT = new SearchHit(CatalogObjectKind.STOP, "stop-1",
            "Berkarar SM", null, 37.95, 58.38, 2.4, "CURATED");

    @Mock
    private CatalogSearchIndexRepository indexRepository;
    @Mock
    private SearchAliasRepository aliasRepository;
    @Mock
    private CatalogSearchCache cache;
    @Mock
    private CorrelationContextService correlationService;
    @Mock
    private EventBus eventBus;

    private SearchCatalogUseCase useCase;

    @BeforeEach
    void setUp() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        useCase = new SearchCatalogUseCase(indexRepository, aliasRepository, cache,
                correlationService, eventBus);
    }

    @Test
    void cacheMissQueriesIndexAndStoresResult() {
        when(aliasRepository.normalize("berkar")).thenReturn(Mono.just("berkar"));
        when(cache.get("berkar", 10)).thenReturn(Mono.empty());
        when(indexRepository.search("berkar", 10)).thenReturn(Flux.just(HIT));
        when(cache.put("berkar", 10, List.of(HIT))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(new SearchCatalogUseCase.Query("berkar", null))))
                .assertNext(result -> {
                    assertThat(result.transitOnly()).isTrue();
                    assertThat(result.items()).hasSize(1);
                    assertThat(result.items().get(0).objectId()).isEqualTo("stop-1");
                })
                .verifyComplete();
        verify(cache).put("berkar", 10, List.of(HIT));
    }

    @Test
    void cacheHitSkipsIndexQuery() {
        when(aliasRepository.normalize("berkar")).thenReturn(Mono.just("berkar"));
        when(cache.get("berkar", 10)).thenReturn(Mono.just(List.of(HIT)));

        StepVerifier.create(useCase.execute(Mono.just(new SearchCatalogUseCase.Query("berkar", null))))
                .assertNext(result -> assertThat(result.items()).hasSize(1))
                .verifyComplete();
        verify(indexRepository, never()).search(anyString(), anyInt());
    }

    @Test
    void emptyNormalizedQueryReturnsEmptyWithoutTouchingCacheOrIndex() {
        when(aliasRepository.normalize("!!!")).thenReturn(Mono.just(""));

        StepVerifier.create(useCase.execute(Mono.just(new SearchCatalogUseCase.Query("!!!", null))))
                .assertNext(result -> assertThat(result.items()).isEmpty())
                .verifyComplete();
        verify(cache, never()).get(anyString(), anyInt());
        verify(indexRepository, never()).search(anyString(), anyInt());
    }

    @Test
    void limitOutOfRangeRejected() {
        StepVerifier.create(useCase.execute(Mono.just(new SearchCatalogUseCase.Query("q", 51))))
                .expectErrorSatisfies(err -> {
                    CatalogSearchValidationException ex =
                            assertInstanceOf(CatalogSearchValidationException.class, err);
                    assertThat(ex.getBusinessCode()).isEqualTo("LIMIT_OUT_OF_RANGE");
                })
                .verify();
    }
}
