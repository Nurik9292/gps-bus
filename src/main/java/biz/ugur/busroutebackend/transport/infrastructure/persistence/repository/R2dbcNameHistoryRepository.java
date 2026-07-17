package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.repository.NameHistoryRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.NameChangeRecord;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class R2dbcNameHistoryRepository implements NameHistoryRepository {

    private static final String COLUMNS =
            "entity_kind, entity_id, field, old_value, new_value, changed_by, changed_at";

    private static final String UPSERT_SQL = """
            INSERT INTO name_history (entity_kind, entity_id, field, old_value, new_value, changed_by, changed_at)
            VALUES (:kind, :entityId, :field, :oldValue, :newValue, :changedBy, :changedAt)
            ON CONFLICT (entity_kind, entity_id, field)
            DO UPDATE SET old_value = EXCLUDED.old_value,
                          new_value = EXCLUDED.new_value,
                          changed_by = EXCLUDED.changed_by,
                          changed_at = EXCLUDED.changed_at
            """;

    private final DatabaseClient databaseClient;

    public R2dbcNameHistoryRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> upsertAll(List<NameChangeRecord> changes) {
        return Flux.fromIterable(changes)
                .concatMap(this::upsertOne)
                .then();
    }

    private Mono<Void> upsertOne(NameChangeRecord change) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(UPSERT_SQL)
                .bind("kind", change.entityKind())
                .bind("entityId", change.entityId())
                .bind("field", change.field())
                .bind("changedAt", change.changedAt());
        spec = bindNullable(spec, "oldValue", change.oldValue());
        spec = bindNullable(spec, "newValue", change.newValue());
        spec = bindNullable(spec, "changedBy", change.changedBy());
        return spec.fetch().rowsUpdated().then();
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                                  String name, String value) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }

    @Override
    public Flux<NameChangeRecord> findByEntity(String entityKind, String entityId) {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM name_history"
                        + " WHERE entity_kind = :kind AND entity_id = :entityId"
                        + " ORDER BY changed_at DESC")
                .bind("kind", entityKind)
                .bind("entityId", entityId)
                .map(R2dbcNameHistoryRepository::mapRecord)
                .all();
    }

    @Override
    public Mono<NameChangeRecord> findByEntityAndField(String entityKind, String entityId, String field) {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM name_history"
                        + " WHERE entity_kind = :kind AND entity_id = :entityId AND field = :field")
                .bind("kind", entityKind)
                .bind("entityId", entityId)
                .bind("field", field)
                .map(R2dbcNameHistoryRepository::mapRecord)
                .one();
    }

    private static NameChangeRecord mapRecord(Readable row) {
        OffsetDateTime changedAt = row.get("changed_at", OffsetDateTime.class);
        return new NameChangeRecord(
                row.get("entity_kind", String.class),
                row.get("entity_id", String.class),
                row.get("field", String.class),
                row.get("old_value", String.class),
                row.get("new_value", String.class),
                row.get("changed_by", String.class),
                changedAt == null ? Instant.now() : changedAt.toInstant());
    }
}
