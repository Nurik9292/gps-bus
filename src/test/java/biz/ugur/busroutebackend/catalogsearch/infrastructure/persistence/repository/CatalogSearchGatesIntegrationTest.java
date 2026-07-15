package biz.ugur.busroutebackend.catalogsearch.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.catalogsearch.application.dto.CreateAliasCommand;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.CreateAliasUseCase;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.DeleteAliasUseCase;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.PreviewCatalogSearchUseCase;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.SearchAliasesUseCase;
import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchCache;
import biz.ugur.busroutebackend.catalogsearch.infrastructure.config.CatalogSearchProperties;
import biz.ugur.busroutebackend.catalogsearch.infrastructure.scheduler.CatalogSearchRebuildScheduler;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataR2dbcTest
@Testcontainers
@Import({R2dbcSearchAliasRepository.class, R2dbcCatalogObjectLookup.class})
class CatalogSearchGatesIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/migration/V97__create_catalog_search.sql"),
                    "/tmp/v97.sql");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url",
                () -> postgres.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @BeforeAll
    static void applySchema() throws Exception {
        var ext = postgres.execInContainer("psql", "-U", "test", "-d", "testdb",
                "-v", "ON_ERROR_STOP=1", "-c", """
                CREATE EXTENSION IF NOT EXISTS pg_trgm;
                CREATE TABLE bus_stops (
                    id VARCHAR(36) PRIMARY KEY,
                    stop_name VARCHAR(200) NOT NULL,
                    name_tm VARCHAR(200),
                    name_en VARCHAR(200),
                    latitude DOUBLE PRECISION NOT NULL DEFAULT 0,
                    longitude DOUBLE PRECISION NOT NULL DEFAULT 0,
                    is_active BOOLEAN DEFAULT true
                );
                CREATE TABLE bus_routes (
                    id VARCHAR(36) PRIMARY KEY,
                    route_number VARCHAR(10) NOT NULL,
                    route_name VARCHAR(200),
                    name_tm VARCHAR(200),
                    name_en VARCHAR(200),
                    is_active BOOLEAN DEFAULT true
                );
                """);
        if (ext.getExitCode() != 0) {
            throw new IllegalStateException("schema bootstrap failed: " + ext.getStderr());
        }
        var v97 = postgres.execInContainer("psql", "-U", "test", "-d", "testdb",
                "-v", "ON_ERROR_STOP=1", "-f", "/tmp/v97.sql");
        if (v97.getExitCode() != 0) {
            throw new IllegalStateException("V97 failed: " + v97.getStderr());
        }
    }

    @Autowired
    private DatabaseClient databaseClient;
    @Autowired
    private R2dbcSearchAliasRepository aliasRepository;
    @Autowired
    private R2dbcCatalogObjectLookup objectLookup;
    @Autowired
    private ReactiveTransactionManager transactionManager;

    private R2dbcCatalogSearchIndexRepository indexRepository;
    private CreateAliasUseCase createUseCase;
    private DeleteAliasUseCase deleteUseCase;
    private PreviewCatalogSearchUseCase previewUseCase;
    private SearchAliasesUseCase searchAliasesUseCase;
    private CatalogSearchRebuildScheduler scheduler;

    @BeforeEach
    void setUp() {
        CatalogSearchProperties properties = new CatalogSearchProperties();
        indexRepository = new R2dbcCatalogSearchIndexRepository(databaseClient, transactionManager, properties);

        CorrelationContextService correlation = mock(CorrelationContextService.class);
        when(correlation.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        SecurityContextService security = mock(SecurityContextService.class);
        when(security.getCurrentAdminId()).thenReturn(Mono.empty());
        EventBus eventBus = mock(EventBus.class);
        TransactionalOperator txOperator = TransactionalOperator.create(transactionManager);
        CatalogSearchCache cache = mock(CatalogSearchCache.class);
        when(cache.evictAll()).thenReturn(Mono.just(0L));

        createUseCase = new CreateAliasUseCase(aliasRepository, indexRepository, objectLookup,
                security, cache, txOperator, correlation, eventBus);
        deleteUseCase = new DeleteAliasUseCase(aliasRepository, indexRepository, cache, txOperator,
                correlation, eventBus);
        previewUseCase = new PreviewCatalogSearchUseCase(indexRepository, aliasRepository, correlation, eventBus);
        searchAliasesUseCase = new SearchAliasesUseCase(aliasRepository, correlation, eventBus);
        scheduler = new CatalogSearchRebuildScheduler(indexRepository);

        databaseClient.sql("DELETE FROM search_alias").then()
                .then(databaseClient.sql("DELETE FROM search_index").then())
                .then(databaseClient.sql("DELETE FROM bus_stops").then())
                .then(databaseClient.sql("DELETE FROM bus_routes").then())
                .block();
    }

    private void seedObjects() {
        databaseClient.sql("""
                INSERT INTO bus_stops (id, stop_name, name_tm) VALUES
                    ('stop-1', 'Berkarar SM', 'Berkarar duralga'),
                    ('stop-2', 'Мир 2/1', NULL)
                """).then()
                .then(databaseClient.sql("""
                        INSERT INTO bus_routes (id, route_number, route_name) VALUES
                            ('route-1', '142', 'Büzmeýin — Täze Zaman')
                        """).then())
                .block();
    }

    private Long countIndexRows() {
        return databaseClient.sql("SELECT count(*) AS cnt FROM search_index")
                .map(row -> row.get("cnt", Long.class)).one().block();
    }

    @Test
    void rebuildOnEmptyTablesYieldsZeroZero() {
        StepVerifier.create(indexRepository.rebuildAll())
                .assertNext(stats -> {
                    assertThat(stats.inserted()).isZero();
                    assertThat(stats.orphanAliases()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void gate10CreatedAliasVisibleInPreviewImmediately() {
        seedObjects();
        StepVerifier.create(createUseCase.execute(Mono.just(new CreateAliasCommand(
                        "STOP", "stop-1", "Русский базар", new BigDecimal("3.0"), "CURATED")))
                .then(previewUseCase.execute(Mono.just(
                        new PreviewCatalogSearchUseCase.Query("русски базар", 10)))))
                .assertNext(result -> {
                    assertThat(result.items()).isNotEmpty();
                    assertThat(result.items().get(0).objectId()).isEqualTo("stop-1");
                    assertThat(result.items().get(0).title()).isEqualTo("Berkarar SM");
                })
                .verifyComplete();
    }

    @Test
    void gate11DeletedAliasDisappearsFromPreviewAndFullRebuildAgrees() {
        seedObjects();
        Long aliasId = createUseCase.execute(Mono.just(new CreateAliasCommand(
                        "STOP", "stop-1", "Уникальнейший", null, "CURATED")))
                .map(r -> r.alias().id())
                .block();
        StepVerifier.create(indexRepository.rebuildAll()).expectNextCount(1).verifyComplete();

        StepVerifier.create(deleteUseCase.execute(Mono.just(aliasId))
                        .then(previewUseCase.execute(Mono.just(
                                new PreviewCatalogSearchUseCase.Query("уникальнейший", 10)))))
                .assertNext(result -> assertThat(result.items()).isEmpty())
                .verifyComplete();

        Long afterPointwise = countIndexRows();
        StepVerifier.create(indexRepository.rebuildAll()).expectNextCount(1).verifyComplete();
        assertThat(countIndexRows()).isEqualTo(afterPointwise);
    }

    @Test
    void gate4CuratedAliasSurvivesFullAndForeignPointwiseRebuild() {
        seedObjects();
        createUseCase.execute(Mono.just(new CreateAliasCommand(
                "STOP", "stop-1", "Русский базар", new BigDecimal("2.0"), "COLLOQUIAL"))).block();

        StepVerifier.create(indexRepository.rebuildAll()).expectNextCount(1).verifyComplete();
        StepVerifier.create(indexRepository.rebuildObject(CatalogObjectKind.STOP, "stop-2"))
                .expectNextCount(1).verifyComplete();

        StepVerifier.create(previewUseCase.execute(Mono.just(
                        new PreviewCatalogSearchUseCase.Query("русски базар", 10))))
                .assertNext(result -> {
                    assertThat(result.items()).isNotEmpty();
                    assertThat(result.items().get(0).objectId()).isEqualTo("stop-1");
                })
                .verifyComplete();
    }

    @Test
    void gate16DuplicateNormsAcrossObjectsFoundByAliasSearch() {
        seedObjects();
        createUseCase.execute(Mono.just(new CreateAliasCommand(
                "STOP", "stop-1", "Мир", null, "CURATED"))).block();
        createUseCase.execute(Mono.just(new CreateAliasCommand(
                "STOP", "stop-2", "Мир", null, "CURATED"))).block();

        StepVerifier.create(searchAliasesUseCase.execute(Mono.just(
                        new SearchAliasesUseCase.Query("мир", 1, 25))))
                .assertNext(list -> {
                    assertThat(list.items()).hasSize(2);
                    assertThat(list.items()).extracting("objectId")
                            .containsExactlyInAnyOrder("stop-1", "stop-2");
                })
                .verifyComplete();
    }

    @Test
    void gate15SchedulerTickRebuildsOnFixture() {
        seedObjects();
        StepVerifier.create(scheduler.rebuildTick())
                .assertNext(stats -> assertThat(stats.inserted()).isGreaterThan(0))
                .verifyComplete();
    }

    @Test
    void gate7WeightRankingAndExactMatchAntiPathology() {
        seedObjects();
        createUseCase.execute(Mono.just(new CreateAliasCommand(
                "STOP", "stop-1", "Народное", new BigDecimal("3.0"), "CURATED"))).block();
        createUseCase.execute(Mono.just(new CreateAliasCommand(
                "STOP", "stop-2", "Народное", new BigDecimal("0.5"), "CURATED"))).block();
        StepVerifier.create(indexRepository.rebuildAll()).expectNextCount(1).verifyComplete();

        StepVerifier.create(previewUseCase.execute(Mono.just(
                        new PreviewCatalogSearchUseCase.Query("народное", 10))))
                .assertNext(result -> {
                    assertThat(result.items()).hasSize(2);
                    assertThat(result.items().get(0).objectId()).isEqualTo("stop-1");
                    assertThat(result.items().get(0).score())
                            .isGreaterThan(result.items().get(1).score());
                })
                .verifyComplete();

        StepVerifier.create(previewUseCase.execute(Mono.just(
                        new PreviewCatalogSearchUseCase.Query("Мир 2 1", 10))))
                .assertNext(result -> {
                    assertThat(result.items()).isNotEmpty();
                    assertThat(result.items().get(0).objectId()).isEqualTo("stop-2");
                })
                .verifyComplete();
    }

    @Test
    void previewFindsTypoAgainstFullNameViaWordSimilarity() {
        seedObjects();
        StepVerifier.create(indexRepository.rebuildAll()
                        .then(previewUseCase.execute(Mono.just(
                                new PreviewCatalogSearchUseCase.Query("Berkark", 10)))))
                .assertNext(result -> {
                    assertThat(result.items()).isNotEmpty();
                    assertThat(result.items().get(0).objectId()).isEqualTo("stop-1");
                })
                .verifyComplete();
    }
}
