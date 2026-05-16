package biz.ugur.busroutebackend.advertising.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Testcontainers
@Import(R2dbcAdPlacementTargetRepository.class)
class R2dbcAdPlacementTargetRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url",
                () -> postgres.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired private DatabaseClient databaseClient;
    @Autowired private R2dbcAdPlacementTargetRepository repository;

    private static final String FAKE_PLACEMENT_PARENT = """
            CREATE TABLE IF NOT EXISTS ad_placements (
                id VARCHAR(36) PRIMARY KEY
            )
            """;

    private static final String TARGETS_TABLE = """
            CREATE TABLE IF NOT EXISTS ad_placement_targets (
                id              VARCHAR(36) PRIMARY KEY,
                placement_id    VARCHAR(36) NOT NULL,
                target_type     VARCHAR(40) NOT NULL,
                target_id       VARCHAR(36),
                created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT ad_placement_targets_placement_fk
                    FOREIGN KEY (placement_id) REFERENCES ad_placements(id) ON DELETE CASCADE,
                CONSTRAINT ad_placement_targets_id_shape_chk
                    CHECK (
                        (target_type IN ('ROUTE','STOP','PLACE') AND target_id IS NOT NULL)
                        OR
                        (target_type IN ('HOME','POPUP','ROUTES_LIST','STOPS_LIST','PLACES_LIST') AND target_id IS NULL)
                    )
            )
            """;

    private static final String UQ_SPECIFIC = """
            CREATE UNIQUE INDEX IF NOT EXISTS uq_apt_specific
                ON ad_placement_targets(placement_id, target_type, target_id)
                WHERE target_id IS NOT NULL
            """;

    private static final String UQ_GENERAL = """
            CREATE UNIQUE INDEX IF NOT EXISTS uq_apt_general
                ON ad_placement_targets(placement_id, target_type)
                WHERE target_id IS NULL
            """;

    @BeforeEach
    void setUp() {
        databaseClient.sql(FAKE_PLACEMENT_PARENT).then().block();
        databaseClient.sql(TARGETS_TABLE).then().block();
        databaseClient.sql(UQ_SPECIFIC).then().block();
        databaseClient.sql(UQ_GENERAL).then().block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("DROP TABLE IF EXISTS ad_placement_targets CASCADE").then().block();
        databaseClient.sql("DROP TABLE IF EXISTS ad_placements CASCADE").then().block();
    }

    private PlacementId insertPlacement() {
        PlacementId id = PlacementId.generate();
        databaseClient.sql("INSERT INTO ad_placements (id) VALUES (:id)")
                .bind("id", id.getValue())
                .fetch()
                .rowsUpdated()
                .block();
        return id;
    }

    @Test
    void replaceAllPersistsTargetsAndFindByPlacementIdReadsThemBack() {
        PlacementId placementId = insertPlacement();
        List<PlacementTarget> targets = List.of(
                PlacementTarget.general(TargetType.HOME),
                PlacementTarget.specific(TargetType.ROUTE, "route-14"),
                PlacementTarget.specific(TargetType.STOP, "stop-007")
        );

        StepVerifier.create(repository.replaceAll(placementId, targets)).verifyComplete();

        StepVerifier.create(repository.findByPlacementId(placementId).collectList())
                .assertNext(loaded -> {
                    assertThat(loaded).hasSize(3);
                    assertThat(loaded).extracting(PlacementTarget::getTargetType)
                            .containsExactlyInAnyOrder(TargetType.HOME, TargetType.ROUTE, TargetType.STOP);
                    assertThat(loaded).extracting(PlacementTarget::getTargetId)
                            .containsExactlyInAnyOrder(null, "route-14", "stop-007");
                })
                .verifyComplete();
    }

    @Test
    void replaceAllWithEmptyListClearsExistingTargets() {
        PlacementId placementId = insertPlacement();
        repository.replaceAll(placementId, List.of(PlacementTarget.general(TargetType.HOME))).block();

        StepVerifier.create(repository.replaceAll(placementId, List.of())).verifyComplete();

        StepVerifier.create(repository.findByPlacementId(placementId).collectList())
                .assertNext(loaded -> assertThat(loaded).isEmpty())
                .verifyComplete();
    }

    @Test
    void deleteByPlacementIdRemovesAll() {
        PlacementId placementId = insertPlacement();
        repository.replaceAll(placementId, List.of(
                PlacementTarget.general(TargetType.POPUP),
                PlacementTarget.specific(TargetType.PLACE, "place-1")
        )).block();

        StepVerifier.create(repository.deleteByPlacementId(placementId)).verifyComplete();

        StepVerifier.create(repository.findByPlacementId(placementId).collectList())
                .assertNext(loaded -> assertThat(loaded).isEmpty())
                .verifyComplete();
    }

    @Test
    void findByPlacementIdsReturnsGroupedMapWithEmptyListsForMissing() {
        PlacementId p1 = insertPlacement();
        PlacementId p2 = insertPlacement();
        PlacementId p3 = insertPlacement();

        repository.replaceAll(p1, List.of(PlacementTarget.general(TargetType.HOME))).block();
        repository.replaceAll(p2, List.of(
                PlacementTarget.general(TargetType.ROUTES_LIST),
                PlacementTarget.specific(TargetType.STOP, "stop-9")
        )).block();

        StepVerifier.create(repository.findByPlacementIds(List.of(p1, p2, p3)))
                .assertNext(map -> {
                    assertThat(map).hasSize(3);
                    assertThat(map.get(p1.getValue())).hasSize(1);
                    assertThat(map.get(p2.getValue())).hasSize(2);
                    assertThat(map.get(p3.getValue())).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void cascadeDeleteRemovesTargetsWhenPlacementGoes() {
        PlacementId placementId = insertPlacement();
        repository.replaceAll(placementId, List.of(
                PlacementTarget.general(TargetType.HOME),
                PlacementTarget.specific(TargetType.ROUTE, "route-1")
        )).block();

        databaseClient.sql("DELETE FROM ad_placements WHERE id = :id")
                .bind("id", placementId.getValue())
                .fetch()
                .rowsUpdated()
                .block();

        StepVerifier.create(repository.findByPlacementId(placementId).collectList())
                .assertNext(loaded -> assertThat(loaded).isEmpty())
                .verifyComplete();
    }

    @Test
    void replaceAllIsIdempotentAcrossCallsThanksToDeleteThenInsert() {
        PlacementId placementId = insertPlacement();
        List<PlacementTarget> first = List.of(PlacementTarget.general(TargetType.HOME));
        List<PlacementTarget> second = List.of(
                PlacementTarget.general(TargetType.POPUP),
                PlacementTarget.specific(TargetType.PLACE, "place-42")
        );

        repository.replaceAll(placementId, first).block();
        repository.replaceAll(placementId, second).block();

        StepVerifier.create(repository.findByPlacementId(placementId).collectList())
                .assertNext(loaded -> {
                    assertThat(loaded).hasSize(2);
                    assertThat(loaded).extracting(PlacementTarget::getTargetType)
                            .containsExactlyInAnyOrder(TargetType.POPUP, TargetType.PLACE);
                })
                .verifyComplete();
    }
}
