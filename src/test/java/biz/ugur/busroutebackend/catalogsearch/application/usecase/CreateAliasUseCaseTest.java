package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.application.dto.CreateAliasCommand;
import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.AliasAlreadyExistsException;
import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogObjectNotFoundException;
import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogSearchValidationException;
import biz.ugur.busroutebackend.catalogsearch.domain.model.AliasCollision;
import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.RebuildStats;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchAlias;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogObjectLookup;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateAliasUseCaseTest {

    @Mock
    private SearchAliasRepository aliasRepository;
    @Mock
    private CatalogSearchIndexRepository indexRepository;
    @Mock
    private CatalogObjectLookup objectLookup;
    @Mock
    private SecurityContextService securityContextService;
    @Mock
    private CorrelationContextService correlationService;
    @Mock
    private EventBus eventBus;

    private CreateAliasUseCase useCase;

    @BeforeEach
    void setUp() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        useCase = new CreateAliasUseCase(aliasRepository, indexRepository, objectLookup,
                securityContextService, correlationService, eventBus);
    }

    private static CreateAliasCommand cmd(String kind, String objectId, String aliasRaw,
                                          BigDecimal weight, String source) {
        return new CreateAliasCommand(kind, objectId, aliasRaw, weight, source);
    }

    private void expectValidation(CreateAliasCommand command, String businessCode) {
        StepVerifier.create(useCase.execute(Mono.just(command)))
                .expectErrorSatisfies(err -> {
                    CatalogSearchValidationException ex =
                            assertInstanceOf(CatalogSearchValidationException.class, err);
                    assertThat(ex.getBusinessCode()).isEqualTo(businessCode);
                })
                .verify();
    }

    @Test
    void happyPathCreatesAliasReindexesAndReturnsCollisions() {
        when(objectLookup.findTitle(CatalogObjectKind.STOP, "stop-1")).thenReturn(Mono.just("Berkarar SM"));
        when(aliasRepository.normalize("Русский базар")).thenReturn(Mono.just("russkii bazar"));
        when(aliasRepository.existsByObjectAndRaw(CatalogObjectKind.STOP, "stop-1", "Русский базар"))
                .thenReturn(Mono.just(false));
        when(securityContextService.getCurrentAdminId()).thenReturn(Mono.just("admin-42"));
        when(aliasRepository.save(any(SearchAlias.class)))
                .thenAnswer(inv -> Mono.just(((SearchAlias) inv.getArgument(0)).toBuilder().id(7L).build()));
        when(indexRepository.rebuildObject(CatalogObjectKind.STOP, "stop-1"))
                .thenReturn(Mono.just(new RebuildStats(3, 0)));
        when(aliasRepository.findCollisions("russkii bazar", CatalogObjectKind.STOP, "stop-1"))
                .thenReturn(Flux.just(new AliasCollision(9L, CatalogObjectKind.STOP, "stop-2", "Мир 2/1")));

        StepVerifier.create(useCase.execute(Mono.just(
                        cmd("STOP", "stop-1", "Русский базар", new BigDecimal("3.0"), "CURATED"))))
                .assertNext(result -> {
                    assertThat(result.alias().id()).isEqualTo(7L);
                    assertThat(result.alias().objectTitle()).isEqualTo("Berkarar SM");
                    assertThat(result.alias().aliasNorm()).isEqualTo("russkii bazar");
                    assertThat(result.alias().createdBy()).isEqualTo("admin-42");
                    assertThat(result.collisions()).hasSize(1);
                    assertThat(result.collisions().get(0).aliasId()).isEqualTo(9L);
                })
                .verifyComplete();
    }

    @Test
    void invalidObjectKindRejected() {
        expectValidation(cmd("CITY", "x", "alias", null, null), "INVALID_OBJECT_KIND");
    }

    @Test
    void blankObjectIdRejected() {
        expectValidation(cmd("STOP", "  ", "alias", null, null), "OBJECT_ID_BLANK");
    }

    @Test
    void missingObjectRejectedWithNotFound() {
        when(objectLookup.findTitle(CatalogObjectKind.STOP, "ghost")).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(Mono.just(cmd("STOP", "ghost", "alias", null, null))))
                .expectErrorSatisfies(err -> {
                    CatalogObjectNotFoundException ex =
                            assertInstanceOf(CatalogObjectNotFoundException.class, err);
                    assertThat(ex.getBusinessCode()).isEqualTo("OBJECT_NOT_FOUND");
                })
                .verify();
    }

    @Test
    void blankAliasRejected() {
        expectValidation(cmd("STOP", "stop-1", "   ", null, null), "ALIAS_BLANK");
    }

    @Test
    void tooLongAliasRejected() {
        expectValidation(cmd("STOP", "stop-1", "a".repeat(301), null, null), "ALIAS_TOO_LONG");
    }

    @Test
    void aliasNormalizingToEmptyRejected() {
        when(objectLookup.findTitle(CatalogObjectKind.STOP, "stop-1")).thenReturn(Mono.just("Berkarar SM"));
        when(aliasRepository.normalize("!!!")).thenReturn(Mono.just(""));
        expectValidation(cmd("STOP", "stop-1", "!!!", null, null), "ALIAS_NORMALIZES_TO_EMPTY");
    }

    @Test
    void duplicateAliasRejectedWithConflict() {
        when(objectLookup.findTitle(CatalogObjectKind.STOP, "stop-1")).thenReturn(Mono.just("Berkarar SM"));
        when(aliasRepository.normalize("dup")).thenReturn(Mono.just("dup"));
        when(aliasRepository.existsByObjectAndRaw(CatalogObjectKind.STOP, "stop-1", "dup"))
                .thenReturn(Mono.just(true));
        StepVerifier.create(useCase.execute(Mono.just(cmd("STOP", "stop-1", "dup", null, null))))
                .expectErrorSatisfies(err -> assertInstanceOf(AliasAlreadyExistsException.class, err))
                .verify();
    }

    @Test
    void weightOutOfRangeRejected() {
        expectValidation(cmd("STOP", "stop-1", "alias", new BigDecimal("3.1"), null), "WEIGHT_OUT_OF_RANGE");
    }

    @Test
    void reservedSourceNameRejected() {
        expectValidation(cmd("STOP", "stop-1", "alias", null, "NAME"), "INVALID_SOURCE");
    }

    @Test
    void raceOnUniqueConstraintMapsToConflict() {
        when(objectLookup.findTitle(CatalogObjectKind.STOP, "stop-1")).thenReturn(Mono.just("Berkarar SM"));
        when(aliasRepository.normalize("racer")).thenReturn(Mono.just("racer"));
        when(aliasRepository.existsByObjectAndRaw(CatalogObjectKind.STOP, "stop-1", "racer"))
                .thenReturn(Mono.just(false));
        when(securityContextService.getCurrentAdminId()).thenReturn(Mono.just("admin-42"));
        when(aliasRepository.save(any(SearchAlias.class)))
                .thenReturn(Mono.error(new org.springframework.dao.DuplicateKeyException("search_alias_uniq")));
        StepVerifier.create(useCase.execute(Mono.just(cmd("STOP", "stop-1", "racer", null, null))))
                .expectErrorSatisfies(err -> assertInstanceOf(AliasAlreadyExistsException.class, err))
                .verify();
    }
}
