package biz.ugur.busroutebackend.advertising.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.advertising.domain.model.AdDetailViewEvent;
import biz.ugur.busroutebackend.advertising.domain.repository.DetailViewSummary;
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
@Import(R2dbcAdDetailViewEventRepository.class)
class R2dbcAdDetailViewEventRepositoryIntegrationTest {

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
    @Autowired private R2dbcAdDetailViewEventRepository repository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        db.sql("""
                CREATE TABLE IF NOT EXISTS ad_detail_view_events (
                    id           UUID         NOT NULL,
                    placement_id UUID         NOT NULL,
                    occurred_at  TIMESTAMPTZ  NOT NULL,
                    duration_ms  INTEGER      NOT NULL CHECK (duration_ms BETWEEN 0 AND 1800000),
                    target_type  VARCHAR(32)  NULL,
                    target_id    UUID         NULL,
                    PRIMARY KEY (id, occurred_at)
                ) PARTITION BY RANGE (occurred_at)
                """).then().block();

        YearMonth now = YearMonth.from(Instant.parse("2026-05-14T12:00:00Z").atZone(ZoneOffset.UTC));
        for (int i = 0; i < 3; i++) {
            YearMonth ym = now.plusMonths(i);
            String partition = "ad_detail_view_events_%04d_%02d".formatted(ym.getYear(), ym.getMonthValue());
            String from = "%04d-%02d-01 00:00:00+00".formatted(ym.getYear(), ym.getMonthValue());
            String to   = "%04d-%02d-01 00:00:00+00".formatted(ym.plusMonths(1).getYear(), ym.plusMonths(1).getMonthValue());
            db.sql("CREATE TABLE IF NOT EXISTS %s PARTITION OF ad_detail_view_events FOR VALUES FROM ('%s') TO ('%s')"
                    .formatted(partition, from, to)).then().block();
            db.sql("CREATE INDEX IF NOT EXISTS ix_%s_placement ON %s (placement_id, occurred_at DESC)"
                    .formatted(partition, partition)).then().block();
        }
    }

    @AfterEach
    void tearDown() {
        db.sql("DROP TABLE IF EXISTS ad_detail_view_events CASCADE").then().block();
    }

    @Test
    void save_insertsRow_withDuration() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        AdDetailViewEvent event = AdDetailViewEvent.newRecord(placementId, 12500, null, null, clock);

        StepVerifier.create(repository.save(event)).verifyComplete();

        Integer ms = db.sql("SELECT duration_ms FROM ad_detail_view_events WHERE id = :id")
                .bind("id", event.id())
                .map(row -> row.get("duration_ms", Integer.class))
                .one().block();
        assertThat(ms).isEqualTo(12500);
    }

    @Test
    void summarize_returnsCountAndAverage() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        repository.save(AdDetailViewEvent.newRecord(placementId, 1000, null, null, clock)).block();
        repository.save(AdDetailViewEvent.newRecord(placementId, 3000, null, null, clock)).block();

        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to   = Instant.parse("2026-06-01T00:00:00Z");

        StepVerifier.create(repository.summarizeByPlacementIdAndOccurredAtBetween(placementId, from, to))
                .assertNext(summary -> {
                    assertThat(summary.count()).isEqualTo(2L);
                    assertThat(summary.avgDurationMs()).isEqualTo(2000L);
                })
                .verifyComplete();
    }

    @Test
    void summarize_emptyResult_returnsZeroCountAndNullAvg() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to   = Instant.parse("2026-06-01T00:00:00Z");

        StepVerifier.create(repository.summarizeByPlacementIdAndOccurredAtBetween(placementId, from, to))
                .assertNext(s -> {
                    assertThat(s.count()).isEqualTo(0L);
                    assertThat(s.avgDurationMs()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void summarizeByDayBetween_groupsByDayWithAvgDuration() {
        PlacementId pid = PlacementId.of(UUID.randomUUID().toString());
        Clock day1 = Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"), ZoneOffset.UTC);
        Clock day2 = Clock.fixed(Instant.parse("2026-05-11T08:00:00Z"), ZoneOffset.UTC);

        repository.save(AdDetailViewEvent.newRecord(pid, 5000, null, null, day1)).block();
        repository.save(AdDetailViewEvent.newRecord(pid, 7000, null, null, day1)).block();
        repository.save(AdDetailViewEvent.newRecord(pid, 3000, null, null, day2)).block();

        Map<LocalDate, DetailViewSummary> result = repository.summarizeByDayBetween(
                pid,
                Instant.parse("2026-05-10T00:00:00Z"),
                Instant.parse("2026-05-12T00:00:00Z")
        ).block();

        assertThat(result.get(LocalDate.parse("2026-05-10")).count()).isEqualTo(2L);
        assertThat(result.get(LocalDate.parse("2026-05-10")).avgDurationMs()).isEqualTo(6000L);
        assertThat(result.get(LocalDate.parse("2026-05-11")).count()).isEqualTo(1L);
        assertThat(result.get(LocalDate.parse("2026-05-11")).avgDurationMs()).isEqualTo(3000L);
    }
}
