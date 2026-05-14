package biz.ugur.busroutebackend.advertising.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdClickEvent;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

@DataR2dbcTest
@Testcontainers
@Import(R2dbcAdClickEventRepository.class)
class R2dbcAdClickEventRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> postgres.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired private DatabaseClient db;
    @Autowired private R2dbcAdClickEventRepository repository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        db.sql("""
                CREATE TABLE IF NOT EXISTS ad_click_events (
                    id           UUID         NOT NULL,
                    placement_id UUID         NOT NULL,
                    occurred_at  TIMESTAMPTZ  NOT NULL,
                    target_type  VARCHAR(32)  NULL,
                    target_id    UUID         NULL,
                    PRIMARY KEY (id, occurred_at)
                ) PARTITION BY RANGE (occurred_at)
                """).then().block();

        YearMonth now = YearMonth.from(Instant.parse("2026-05-14T12:00:00Z").atZone(ZoneOffset.UTC));
        for (int i = 0; i < 3; i++) {
            YearMonth ym = now.plusMonths(i);
            String partition = "ad_click_events_%04d_%02d".formatted(ym.getYear(), ym.getMonthValue());
            String from = "%04d-%02d-01 00:00:00+00".formatted(ym.getYear(), ym.getMonthValue());
            String to   = "%04d-%02d-01 00:00:00+00".formatted(ym.plusMonths(1).getYear(), ym.plusMonths(1).getMonthValue());
            db.sql("CREATE TABLE IF NOT EXISTS %s PARTITION OF ad_click_events FOR VALUES FROM ('%s') TO ('%s')"
                    .formatted(partition, from, to)).then().block();
            db.sql("CREATE INDEX IF NOT EXISTS ix_%s_placement ON %s (placement_id, occurred_at DESC)"
                    .formatted(partition, partition)).then().block();
        }
    }

    @AfterEach
    void tearDown() {
        db.sql("DROP TABLE IF EXISTS ad_click_events CASCADE").then().block();
    }

    @Test
    void save_insertsRow() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        AdClickEvent event = AdClickEvent.newRecord(placementId, TargetType.POPUP, null, clock);

        StepVerifier.create(repository.save(event)).verifyComplete();

        Long cnt = db.sql("SELECT COUNT(*) FROM ad_click_events WHERE id = :id")
                .bind("id", event.id())
                .map(row -> row.get(0, Long.class))
                .one().block();
        org.assertj.core.api.Assertions.assertThat(cnt).isEqualTo(1L);
    }

    @Test
    void save_withNullTargets_succeeds() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        AdClickEvent event = AdClickEvent.newRecord(placementId, null, null, clock);

        StepVerifier.create(repository.save(event)).verifyComplete();
    }

    @Test
    void countByPlacementIdAndOccurredAtBetween_returnsExactCount() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        for (int i = 0; i < 2; i++) {
            repository.save(AdClickEvent.newRecord(placementId, null, null, clock)).block();
        }

        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to   = Instant.parse("2026-06-01T00:00:00Z");

        StepVerifier.create(repository.countByPlacementIdAndOccurredAtBetween(placementId, from, to))
                .expectNext(2L)
                .verifyComplete();
    }
}
