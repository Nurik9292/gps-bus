package biz.ugur.busroutebackend.advertising.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.advertising.domain.model.AdDetailViewEvent;
import biz.ugur.busroutebackend.advertising.domain.repository.AdDetailViewEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.DetailViewSummary;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcAdDetailViewEventRepository implements AdDetailViewEventRepository {

    private final DatabaseClient db;

    @Override
    public Mono<Void> save(AdDetailViewEvent event) {
        GenericExecuteSpec spec = db.sql("""
                        INSERT INTO ad_detail_view_events
                          (id, placement_id, occurred_at, duration_ms, target_type, target_id)
                        VALUES
                          (:id, :placement_id, :occurred_at, :duration_ms, :target_type, :target_id)
                        """)
                .bind("id",           event.id())
                .bind("placement_id", UUID.fromString(event.placementId().getValue()))
                .bind("occurred_at",  event.occurredAt())
                .bind("duration_ms",  event.durationMs());

        spec = event.targetType() != null
                ? spec.bind("target_type", event.targetType().name())
                : spec.bindNull("target_type", String.class);
        spec = event.targetId() != null
                ? spec.bind("target_id", event.targetId())
                : spec.bindNull("target_id", UUID.class);

        return spec.fetch().rowsUpdated().then();
    }

    @Override
    public Mono<DetailViewSummary> summarizeByPlacementIdAndOccurredAtBetween(
            PlacementId placementId, Instant from, Instant to) {
        return db.sql("""
                        SELECT COUNT(*)::BIGINT AS cnt, AVG(duration_ms)::BIGINT AS avg_ms
                        FROM ad_detail_view_events
                        WHERE placement_id = :placement_id
                          AND occurred_at >= :from
                          AND occurred_at <  :to
                        """)
                .bind("placement_id", UUID.fromString(placementId.getValue()))
                .bind("from",         from)
                .bind("to",           to)
                .map(row -> new DetailViewSummary(
                        row.get("cnt", Long.class),
                        row.get("avg_ms", Long.class)
                ))
                .one();
    }
}
