package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Testcontainers
@Import(R2dbcSegmentTravelStatsRepository.class)
class R2dbcSegmentTravelStatsRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> postgres.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    private static final String ROUTE = "160";
    private static final String ROUTE_ID = "route-legacy-160";
    private static final int DIRECTION = 0;
    private static final String FROM = "stop-A";
    private static final String TO = "stop-B";
    private static final int HOUR = 8;
    private static final boolean WEEKEND = false;

    @Autowired
    private DatabaseClient databaseClient;

    private R2dbcSegmentTravelStatsRepository repository;

    @BeforeEach
    void setUp() {
        repository = new R2dbcSegmentTravelStatsRepository(databaseClient);

        databaseClient.sql("CREATE EXTENSION IF NOT EXISTS pgcrypto").then().block();

        String createTableSql = """
                CREATE TABLE IF NOT EXISTS segment_travel_stats (
                    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    route_id              VARCHAR(100) NOT NULL,
                    route_number          VARCHAR(32)  NOT NULL,
                    direction             INTEGER      NOT NULL,
                    from_stop_id          VARCHAR(100) NOT NULL,
                    to_stop_id            VARCHAR(100) NOT NULL,
                    hour_of_day           SMALLINT     NOT NULL,
                    is_weekend            BOOLEAN      NOT NULL,
                    avg_travel_seconds    DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                    sample_count          BIGINT       NOT NULL DEFAULT 0,
                    last_observed_at      TIMESTAMP WITH TIME ZONE,
                    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT segment_travel_stats_unique UNIQUE
                        (route_id, direction, from_stop_id, to_stop_id, hour_of_day, is_weekend)
                )
                """;

        databaseClient.sql(createTableSql).then().block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("DROP TABLE IF EXISTS segment_travel_stats CASCADE").then().block();
    }

    @Test
    void save_persistsNewStat() {
        SegmentTravelStat stat = SegmentTravelStat.initial(ROUTE_ID, ROUTE, DIRECTION, FROM, TO, HOUR, WEEKEND)
                .withNewSample(120.5, Instant.parse("2026-04-27T10:00:00Z"));

        StepVerifier.create(repository.save(stat))
                .assertNext(saved -> {
                    assertThat(saved.getRouteNumber()).isEqualTo(ROUTE);
                    assertThat(saved.getAvgTravelSeconds()).isEqualTo(120.5);
                    assertThat(saved.getSampleCount()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void findByKey_retrievesPersistedStat() {
        SegmentTravelStat stat = SegmentTravelStat.initial(ROUTE_ID, ROUTE, DIRECTION, FROM, TO, HOUR, WEEKEND)
                .withNewSample(180.0, Instant.parse("2026-04-27T08:30:00Z"));
        repository.save(stat).block();

        StepVerifier.create(repository.findByKey(ROUTE_ID, DIRECTION, FROM, TO, HOUR, WEEKEND))
                .assertNext(found -> {
                    assertThat(found.getRouteNumber()).isEqualTo(ROUTE);
                    assertThat(found.getDirection()).isEqualTo(DIRECTION);
                    assertThat(found.getFromStopId()).isEqualTo(FROM);
                    assertThat(found.getToStopId()).isEqualTo(TO);
                    assertThat(found.getHourOfDay()).isEqualTo(HOUR);
                    assertThat(found.isWeekend()).isEqualTo(WEEKEND);
                    assertThat(found.getAvgTravelSeconds()).isEqualTo(180.0);
                    assertThat(found.getSampleCount()).isEqualTo(1);
                    assertThat(found.getLastObservedAt())
                            .isEqualTo(Instant.parse("2026-04-27T08:30:00Z"));
                })
                .verifyComplete();
    }

    @Test
    void findByKey_returnsEmptyWhenNotFound() {
        StepVerifier.create(repository.findByKey("route-legacy-999", 1, "missing-from", "missing-to", 23, true))
                .verifyComplete();
    }

    @Test
    void save_upsertUpdatesExistingKeyInPlace() {
        SegmentTravelStat first = SegmentTravelStat.initial(ROUTE_ID, ROUTE, DIRECTION, FROM, TO, HOUR, WEEKEND)
                .withNewSample(100.0, Instant.parse("2026-04-27T08:00:00Z"));
        repository.save(first).block();

        SegmentTravelStat updated = first
                .withNewSample(140.0, Instant.parse("2026-04-27T09:00:00Z"));
        repository.save(updated).block();

        StepVerifier.create(repository.findByKey(ROUTE_ID, DIRECTION, FROM, TO, HOUR, WEEKEND))
                .assertNext(found -> {
                    assertThat(found.getSampleCount()).isEqualTo(2);
                    assertThat(found.getAvgTravelSeconds()).isEqualTo(120.0);
                    assertThat(found.getLastObservedAt())
                            .isEqualTo(Instant.parse("2026-04-27T09:00:00Z"));
                })
                .verifyComplete();

        StepVerifier.create(repository.findAll().count())
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    void findAll_returnsAllPersistedStats() {
        repository.save(SegmentTravelStat.initial(ROUTE_ID, ROUTE, 0, FROM, TO, 8, false)
                .withNewSample(100.0, Instant.now())).block();
        repository.save(SegmentTravelStat.initial(ROUTE_ID, ROUTE, 1, FROM, TO, 8, false)
                .withNewSample(110.0, Instant.now())).block();
        repository.save(SegmentTravelStat.initial(ROUTE_ID, ROUTE, 0, FROM, TO, 17, false)
                .withNewSample(150.0, Instant.now())).block();
        repository.save(SegmentTravelStat.initial(ROUTE_ID, ROUTE, 0, FROM, TO, 8, true)
                .withNewSample(80.0, Instant.now())).block();

        StepVerifier.create(repository.findAll().count())
                .expectNext(4L)
                .verifyComplete();
    }

    @Test
    void differentTimeBucketsAreIsolatedEntries() {
        SegmentTravelStat morning = SegmentTravelStat.initial(ROUTE_ID, ROUTE, DIRECTION, FROM, TO, 8, false)
                .withNewSample(120.0, Instant.now());
        SegmentTravelStat evening = SegmentTravelStat.initial(ROUTE_ID, ROUTE, DIRECTION, FROM, TO, 17, false)
                .withNewSample(180.0, Instant.now());
        repository.save(morning).block();
        repository.save(evening).block();

        StepVerifier.create(repository.findByKey(ROUTE_ID, DIRECTION, FROM, TO, 8, false))
                .assertNext(found -> assertThat(found.getAvgTravelSeconds()).isEqualTo(120.0))
                .verifyComplete();

        StepVerifier.create(repository.findByKey(ROUTE_ID, DIRECTION, FROM, TO, 17, false))
                .assertNext(found -> assertThat(found.getAvgTravelSeconds()).isEqualTo(180.0))
                .verifyComplete();
    }

    @Test
    void weekendVsWeekdayAreSeparateEntries() {
        SegmentTravelStat weekday = SegmentTravelStat.initial(ROUTE_ID, ROUTE, DIRECTION, FROM, TO, HOUR, false)
                .withNewSample(100.0, Instant.now());
        SegmentTravelStat weekend = SegmentTravelStat.initial(ROUTE_ID, ROUTE, DIRECTION, FROM, TO, HOUR, true)
                .withNewSample(140.0, Instant.now());
        repository.save(weekday).block();
        repository.save(weekend).block();

        StepVerifier.create(repository.findByKey(ROUTE_ID, DIRECTION, FROM, TO, HOUR, false))
                .assertNext(found -> assertThat(found.getAvgTravelSeconds()).isEqualTo(100.0))
                .verifyComplete();

        StepVerifier.create(repository.findByKey(ROUTE_ID, DIRECTION, FROM, TO, HOUR, true))
                .assertNext(found -> assertThat(found.getAvgTravelSeconds()).isEqualTo(140.0))
                .verifyComplete();
    }

    @Test
    void roundTripPreservesNullLastObservedAt() {
        SegmentTravelStat stat = SegmentTravelStat.initial(ROUTE_ID, ROUTE, DIRECTION, FROM, TO, HOUR, WEEKEND);
        repository.save(stat).block();

        StepVerifier.create(repository.findByKey(ROUTE_ID, DIRECTION, FROM, TO, HOUR, WEEKEND))
                .assertNext(found -> {
                    assertThat(found.getSampleCount()).isZero();
                    assertThat(found.getAvgTravelSeconds()).isZero();
                    assertThat(found.getLastObservedAt()).isNull();
                })
                .verifyComplete();
    }
}
