package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.application.dto.PatchAliasCommand;
import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.AliasNotFoundException;
import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogSearchValidationException;
import biz.ugur.busroutebackend.catalogsearch.domain.model.AliasSource;
import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.RebuildStats;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchAlias;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogObjectLookup;
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
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PatchAndDeleteAliasUseCaseTest {

    @Mock
    private SearchAliasRepository aliasRepository;
    @Mock
    private CatalogSearchIndexRepository indexRepository;
    @Mock
    private CatalogObjectLookup objectLookup;
    @Mock
    private CatalogSearchCache cache;
    @Mock
    private TransactionalOperator transactionalOperator;
    @Mock
    private CorrelationContextService correlationService;
    @Mock
    private EventBus eventBus;

    private PatchAliasUseCase patchUseCase;
    private DeleteAliasUseCase deleteUseCase;

    private static final SearchAlias EXISTING = SearchAlias.builder()
            .id(7L).objectKind(CatalogObjectKind.STOP).objectId("stop-1")
            .aliasRaw("Русский базар").aliasNorm("russkii bazar")
            .weight(new BigDecimal("1.0")).source(AliasSource.CURATED)
            .createdAt(java.time.Instant.EPOCH).updatedAt(java.time.Instant.EPOCH)
            .build();

    @BeforeEach
    void setUp() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cache.evictAll()).thenReturn(Mono.just(0L));
        patchUseCase = new PatchAliasUseCase(aliasRepository, indexRepository, objectLookup,
                cache, transactionalOperator, correlationService, eventBus);
        deleteUseCase = new DeleteAliasUseCase(aliasRepository, indexRepository,
                cache, transactionalOperator, correlationService, eventBus);
    }

    @Test
    void patchHappyUpdatesWeightAndReindexes() {
        when(aliasRepository.findById(7L)).thenReturn(Mono.just(EXISTING));
        when(aliasRepository.save(any(SearchAlias.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(indexRepository.rebuildObject(CatalogObjectKind.STOP, "stop-1"))
                .thenReturn(Mono.just(new RebuildStats(2, 0)));
        when(objectLookup.findTitle(CatalogObjectKind.STOP, "stop-1")).thenReturn(Mono.just("Berkarar SM"));

        StepVerifier.create(patchUseCase.execute(Mono.just(
                        new PatchAliasCommand(7L, new BigDecimal("2.5"), null, false))))
                .assertNext(result -> {
                    assertThat(result.weight()).isEqualByComparingTo("2.5");
                    assertThat(result.source()).isEqualTo("CURATED");
                })
                .verifyComplete();
        verify(indexRepository).rebuildObject(CatalogObjectKind.STOP, "stop-1");
    }

    @Test
    void patchWeightOutOfRangeRejected() {
        StepVerifier.create(patchUseCase.execute(Mono.just(
                        new PatchAliasCommand(7L, new BigDecimal("5.0"), null, false))))
                .expectErrorSatisfies(err -> {
                    CatalogSearchValidationException ex =
                            assertInstanceOf(CatalogSearchValidationException.class, err);
                    assertThat(ex.getBusinessCode()).isEqualTo("WEIGHT_OUT_OF_RANGE");
                })
                .verify();
    }

    @Test
    void patchInvalidSourceRejected() {
        StepVerifier.create(patchUseCase.execute(Mono.just(
                        new PatchAliasCommand(7L, null, "NAME", false))))
                .expectErrorSatisfies(err -> {
                    CatalogSearchValidationException ex =
                            assertInstanceOf(CatalogSearchValidationException.class, err);
                    assertThat(ex.getBusinessCode()).isEqualTo("INVALID_SOURCE");
                })
                .verify();
    }

    @Test
    void patchUnknownIdRejectedWithNotFound() {
        when(aliasRepository.findById(404L)).thenReturn(Mono.empty());
        StepVerifier.create(patchUseCase.execute(Mono.just(
                        new PatchAliasCommand(404L, new BigDecimal("1.0"), null, false))))
                .expectErrorSatisfies(err -> {
                    AliasNotFoundException ex = assertInstanceOf(AliasNotFoundException.class, err);
                    assertThat(ex.getBusinessCode()).isEqualTo("ALIAS_NOT_FOUND");
                })
                .verify();
    }

    @Test
    void patchWithAliasRawRejectedAsImmutable() {
        StepVerifier.create(patchUseCase.execute(Mono.just(
                        new PatchAliasCommand(7L, null, null, true))))
                .expectErrorSatisfies(err -> {
                    CatalogSearchValidationException ex =
                            assertInstanceOf(CatalogSearchValidationException.class, err);
                    assertThat(ex.getBusinessCode()).isEqualTo("ALIAS_RAW_IMMUTABLE");
                })
                .verify();
    }

    @Test
    void deleteHappyRemovesAndReindexes() {
        when(aliasRepository.findById(7L)).thenReturn(Mono.just(EXISTING));
        when(aliasRepository.deleteById(7L)).thenReturn(Mono.empty());
        when(indexRepository.rebuildObject(CatalogObjectKind.STOP, "stop-1"))
                .thenReturn(Mono.just(new RebuildStats(1, 0)));

        StepVerifier.create(deleteUseCase.execute(Mono.just(7L)))
                .verifyComplete();
        verify(aliasRepository).deleteById(7L);
        verify(indexRepository).rebuildObject(CatalogObjectKind.STOP, "stop-1");
    }

    @Test
    void deleteUnknownIdRejectedWithNotFound() {
        when(aliasRepository.findById(404L)).thenReturn(Mono.empty());
        StepVerifier.create(deleteUseCase.execute(Mono.just(404L)))
                .expectErrorSatisfies(err -> assertInstanceOf(AliasNotFoundException.class, err))
                .verify();
    }
}
