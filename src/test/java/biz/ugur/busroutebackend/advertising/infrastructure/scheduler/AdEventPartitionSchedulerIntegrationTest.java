package biz.ugur.busroutebackend.advertising.infrastructure.scheduler;

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

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Testcontainers
@Import(AdEventPartitionAlertProperties.class)
class AdEventPartitionSchedulerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> postgres.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired private DatabaseClient db;
    @Autowired private AdEventPartitionAlertProperties properties;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T03:00:00Z"), ZoneOffset.UTC);
    private AdEventPartitionScheduler scheduler;

    @BeforeEach
    void setUp() {
        db.sql("""
                CREATE TABLE IF NOT EXISTS ad_impression_events (
                    id UUID NOT NULL,
                    placement_id UUID NOT NULL,
                    occurred_at TIMESTAMPTZ NOT NULL,
                    target_type VARCHAR(32) NULL,
                    target_id UUID NULL,
                    PRIMARY KEY (id, occurred_at)
                ) PARTITION BY RANGE (occurred_at)
                """).then().block();
        db.sql("""
                CREATE TABLE IF NOT EXISTS ad_click_events (
                    id UUID NOT NULL, placement_id UUID NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
                    target_type VARCHAR(32) NULL, target_id UUID NULL,
                    PRIMARY KEY (id, occurred_at)
                ) PARTITION BY RANGE (occurred_at)
                """).then().block();
        db.sql("""
                CREATE TABLE IF NOT EXISTS ad_detail_view_events (
                    id UUID NOT NULL, placement_id UUID NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
                    duration_ms INTEGER NOT NULL CHECK (duration_ms BETWEEN 0 AND 1800000),
                    target_type VARCHAR(32) NULL, target_id UUID NULL,
                    PRIMARY KEY (id, occurred_at)
                ) PARTITION BY RANGE (occurred_at)
                """).then().block();

        properties.setLookaheadMonths(2);
        scheduler = new AdEventPartitionScheduler(db, clock, properties);
    }

    @AfterEach
    void tearDown() {
        db.sql("DROP TABLE IF EXISTS ad_impression_events CASCADE").then().block();
        db.sql("DROP TABLE IF EXISTS ad_click_events CASCADE").then().block();
        db.sql("DROP TABLE IF EXISTS ad_detail_view_events CASCADE").then().block();
    }

    @Test
    void ensurePartitions_createsNewPartitions_andIsIdempotent() {
        scheduler.ensurePartitions();
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        YearMonth target = YearMonth.from(Instant.parse("2026-05-14T03:00:00Z").atZone(ZoneOffset.UTC)).plusMonths(2);
        String partition = "ad_impression_events_%04d_%02d".formatted(target.getYear(), target.getMonthValue());

        Long count = db.sql("SELECT COUNT(*) FROM pg_class WHERE relname = :name")
                .bind("name", partition)
                .map(row -> row.get(0, Long.class))
                .one().block();
        assertThat(count).isEqualTo(1L);

        scheduler.ensurePartitions();
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        count = db.sql("SELECT COUNT(*) FROM pg_class WHERE relname = :name")
                .bind("name", partition)
                .map(row -> row.get(0, Long.class))
                .one().block();
        assertThat(count).isEqualTo(1L);
    }
}
