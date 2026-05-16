package biz.ugur.busroutebackend.advertising.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdImpressionEvent;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Testcontainers
@Import(R2dbcAdImpressionEventRepository.class)
class R2dbcAdImpressionEventRepositoryIntegrationTest {

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
    @Autowired private R2dbcAdImpressionEventRepository repository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        db.sql("""
                CREATE TABLE IF NOT EXISTS ad_impression_events (
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
            String partition = "ad_impression_events_%04d_%02d".formatted(ym.getYear(), ym.getMonthValue());
            String from = "%04d-%02d-01 00:00:00+00".formatted(ym.getYear(), ym.getMonthValue());
            String to   = "%04d-%02d-01 00:00:00+00".formatted(ym.plusMonths(1).getYear(), ym.plusMonths(1).getMonthValue());
            db.sql("CREATE TABLE IF NOT EXISTS %s PARTITION OF ad_impression_events FOR VALUES FROM ('%s') TO ('%s')"
                    .formatted(partition, from, to)).then().block();
            db.sql("CREATE INDEX IF NOT EXISTS ix_%s_placement ON %s (placement_id, occurred_at DESC)"
                    .formatted(partition, partition)).then().block();
        }
    }

    @AfterEach
    void tearDown() {
        db.sql("DROP TABLE IF EXISTS ad_impression_events CASCADE").then().block();
    }

    @Test
    void save_insertsRow_withAllFields() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        UUID targetId = UUID.randomUUID();
        AdImpressionEvent event = AdImpressionEvent.newRecord(placementId, TargetType.ROUTE, targetId, clock);

        StepVerifier.create(repository.save(event)).verifyComplete();

        Long cnt = db.sql("SELECT COUNT(*) FROM ad_impression_events WHERE id = :id")
                .bind("id", event.id())
                .map(row -> row.get(0, Long.class))
                .one().block();
        assertThat(cnt).isEqualTo(1L);
    }

    @Test
    void save_withNullTargetTypeAndTargetId_succeeds() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        AdImpressionEvent event = AdImpressionEvent.newRecord(placementId, null, null, clock);

        StepVerifier.create(repository.save(event)).verifyComplete();
    }

    @Test
    void countByPlacementIdAndOccurredAtBetween_returnsExactCount() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        for (int i = 0; i < 3; i++) {
            repository.save(AdImpressionEvent.newRecord(placementId, null, null, clock)).block();
        }
        repository.save(AdImpressionEvent.newRecord(
                PlacementId.of(UUID.randomUUID().toString()), null, null, clock)).block();

        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to   = Instant.parse("2026-06-01T00:00:00Z");

        StepVerifier.create(repository.countByPlacementIdAndOccurredAtBetween(placementId, from, to))
                .expectNext(3L)
                .verifyComplete();
    }

    @Test
    void countByPlacementIdAndOccurredAtBetween_excludesBoundaryAfterTo() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        repository.save(AdImpressionEvent.newRecord(placementId, null, null, clock)).block();

        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to   = Instant.parse("2026-05-14T12:00:00Z");

        StepVerifier.create(repository.countByPlacementIdAndOccurredAtBetween(placementId, from, to))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void countByDayBetween_groupsByDay() {
        PlacementId pid = PlacementId.of(UUID.randomUUID().toString());
        Clock day1 = Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"), ZoneOffset.UTC);
        Clock day2 = Clock.fixed(Instant.parse("2026-05-11T08:00:00Z"), ZoneOffset.UTC);
        Clock day3 = Clock.fixed(Instant.parse("2026-05-11T20:00:00Z"), ZoneOffset.UTC);

        repository.save(AdImpressionEvent.newRecord(pid, null, null, day1)).block();
        repository.save(AdImpressionEvent.newRecord(pid, null, null, day2)).block();
        repository.save(AdImpressionEvent.newRecord(pid, null, null, day3)).block();

        Map<LocalDate, Long> result = repository.countByDayBetween(
                pid,
                Instant.parse("2026-05-10T00:00:00Z"),
                Instant.parse("2026-05-12T00:00:00Z")
        ).block();

        assertThat(result)
                .containsEntry(LocalDate.parse("2026-05-10"), 1L)
                .containsEntry(LocalDate.parse("2026-05-11"), 2L);
    }

    @Test
    void countByOccurredAtBetween_acrossAllPlacements() {
        PlacementId p1 = PlacementId.of(UUID.randomUUID().toString());
        PlacementId p2 = PlacementId.of(UUID.randomUUID().toString());
        Clock c = Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"), ZoneOffset.UTC);

        repository.save(AdImpressionEvent.newRecord(p1, null, null, c)).block();
        repository.save(AdImpressionEvent.newRecord(p1, null, null, c)).block();
        repository.save(AdImpressionEvent.newRecord(p2, null, null, c)).block();

        Long total = repository.countByOccurredAtBetween(
                Instant.parse("2026-05-09T00:00:00Z"),
                Instant.parse("2026-05-11T00:00:00Z")
        ).block();
        assertThat(total).isEqualTo(3L);
    }
}
