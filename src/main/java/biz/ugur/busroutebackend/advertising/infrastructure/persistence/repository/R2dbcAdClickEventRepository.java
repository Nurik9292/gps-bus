package biz.ugur.busroutebackend.advertising.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.advertising.domain.model.AdClickEvent;
import biz.ugur.busroutebackend.advertising.domain.repository.AdClickEventRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcAdClickEventRepository implements AdClickEventRepository {

    private final DatabaseClient db;

    @Override
    public Mono<Void> save(AdClickEvent event) {
        GenericExecuteSpec spec = db.sql("""
                        INSERT INTO ad_click_events
                          (id, placement_id, occurred_at, target_type, target_id)
                        VALUES
                          (:id, :placement_id, :occurred_at, :target_type, :target_id)
                        """)
                .bind("id",           event.id())
                .bind("placement_id", UUID.fromString(event.placementId().getValue()))
                .bind("occurred_at",  event.occurredAt());

        spec = event.targetType() != null
                ? spec.bind("target_type", event.targetType().name())
                : spec.bindNull("target_type", String.class);
        spec = event.targetId() != null
                ? spec.bind("target_id", event.targetId())
                : spec.bindNull("target_id", UUID.class);

        return spec.fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Long> countByPlacementIdAndOccurredAtBetween(PlacementId placementId, Instant from, Instant to) {
        return db.sql("""
                        SELECT COUNT(*)::BIGINT AS cnt
                        FROM ad_click_events
                        WHERE placement_id = :placement_id
                          AND occurred_at >= :from
                          AND occurred_at <  :to
                        """)
                .bind("placement_id", UUID.fromString(placementId.getValue()))
                .bind("from",         from)
                .bind("to",           to)
                .map(row -> row.get("cnt", Long.class))
                .one();
    }

    @Override
    public Mono<Map<LocalDate, Long>> countByDayBetween(PlacementId placementId, Instant from, Instant to) {
        return db.sql("""
                        SELECT DATE_TRUNC('day', occurred_at AT TIME ZONE 'UTC')::DATE AS day,
                               COUNT(*)::BIGINT                                        AS cnt
                        FROM ad_click_events
                        WHERE placement_id = :placement_id
                          AND occurred_at >= :from
                          AND occurred_at <  :to
                        GROUP BY day
                        ORDER BY day
                        """)
                .bind("placement_id", UUID.fromString(placementId.getValue()))
                .bind("from",         from)
                .bind("to",           to)
                .map(row -> Map.entry(
                        row.get("day", LocalDate.class),
                        row.get("cnt", Long.class)))
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    @Override
    public Mono<Long> countByOccurredAtBetween(Instant from, Instant to) {
        return db.sql("""
                        SELECT COUNT(*)::BIGINT AS cnt
                        FROM ad_click_events
                        WHERE occurred_at >= :from
                          AND occurred_at <  :to
                        """)
                .bind("from", from)
                .bind("to",   to)
                .map(row -> row.get("cnt", Long.class))
                .one();
    }

    @Override
    public Mono<Void> deleteByPlacementId(PlacementId placementId) {
        return db.sql("DELETE FROM ad_click_events WHERE placement_id = :placement_id")
                .bind("placement_id", UUID.fromString(placementId.getValue()))
                .fetch()
                .rowsUpdated()
                .then();
    }
}
