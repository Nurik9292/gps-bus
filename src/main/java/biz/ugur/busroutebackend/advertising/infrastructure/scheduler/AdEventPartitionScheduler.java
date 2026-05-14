package biz.ugur.busroutebackend.advertising.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "advertising.events.partition", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdEventPartitionScheduler {

    private static final List<String> TABLES = List.of(
            "ad_impression_events",
            "ad_click_events",
            "ad_detail_view_events"
    );

    private final DatabaseClient db;
    private final Clock clock;
    private final AdEventPartitionAlertProperties properties;

    @Scheduled(cron = "${advertising.events.partition.cron:0 0 3 * * *}",
               zone = "${advertising.events.partition.zone:UTC}")
    public void ensurePartitions() {
        YearMonth now = YearMonth.from(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
        int lookahead = properties.getLookaheadMonths();

        Flux.fromIterable(TABLES)
                .flatMap(table -> ensureTablePartitions(table, now, lookahead), 1)
                .subscribe(
                        v -> {},
                        err -> {
                            log.error("Partition maintenance failed", err);
                            sendAlert(err);
                        }
                );
    }

    private Mono<Void> ensureTablePartitions(String table, YearMonth now, int lookahead) {
        return Flux.range(0, lookahead + 1)
                .map(now::plusMonths)
                .flatMap(ym -> createPartitionIfMissing(table, ym), 1)
                .then();
    }

    private Mono<Void> createPartitionIfMissing(String table, YearMonth ym) {
        String partition = "%s_%04d_%02d".formatted(table, ym.getYear(), ym.getMonthValue());
        YearMonth next   = ym.plusMonths(1);
        String from      = "%04d-%02d-01 00:00:00+00".formatted(ym.getYear(), ym.getMonthValue());
        String to        = "%04d-%02d-01 00:00:00+00".formatted(next.getYear(), next.getMonthValue());

        String createTable = """
                CREATE TABLE IF NOT EXISTS %s PARTITION OF %s
                  FOR VALUES FROM ('%s') TO ('%s')
                """.formatted(partition, table, from, to);

        String createPlacementIndex = """
                CREATE INDEX IF NOT EXISTS ix_%s_placement
                  ON %s (placement_id, occurred_at DESC)
                """.formatted(partition, partition);

        String createTargetIndex = """
                CREATE INDEX IF NOT EXISTS ix_%s_target
                  ON %s (target_type, occurred_at DESC) WHERE target_type IS NOT NULL
                """.formatted(partition, partition);

        return db.sql(createTable).fetch().rowsUpdated().then()
                .then(db.sql(createPlacementIndex).fetch().rowsUpdated().then())
                .then(db.sql(createTargetIndex).fetch().rowsUpdated().then());
    }

    private void sendAlert(Throwable err) {
        log.warn("Partition maintenance alert triggered", err);
    }
}
