package biz.ugur.busroutebackend.shared.infrastructure.persistence;

import biz.ugur.busroutebackend.shared.base.BaseEntity;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseR2dbcRepository<T extends BaseEntity<ID>, ID> implements BaseRepository<T, ID> {

    protected final DatabaseClient databaseClient;
    protected final String tableName;
    protected final Class<T> entityClass;

    protected BaseR2dbcRepository(DatabaseClient databaseClient, String tableName, Class<T> entityClass) {
        this.databaseClient = databaseClient;
        this.tableName = tableName;
        this.entityClass = entityClass;
    }

    @Override
    public Mono<T> save(T entity) {
        return findById(entity.getId())
                .flatMap(existing -> {
                    entity.setVersion(existing.getVersion());
                    return update(entity);
                })
                .switchIfEmpty(insert(entity)) .doOnSuccess(savedEntity -> {
                    if (savedEntity instanceof AggregateRoot<?, ?> aggregate) {

                    }
                });
    }

    @Override
    public Flux<T> findAll() {
        String sql = String.format("SELECT * FROM %s", tableName);

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all();
    }


    @Override
    public Flux<T> findAll(Pageable pageable) {
        String sql = String.format(
                "SELECT * FROM %s %s LIMIT :limit OFFSET :offset",
                tableName,
                getOrderByClause(pageable)
        );

        return databaseClient.sql(sql)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all();
    }


    @Override
    public Mono<T> findById(ID id) {
        String sql = String.format("SELECT * FROM %s WHERE id = :id", tableName);

        return databaseClient.sql(sql)
                .bind("id", convertIdToDatabase(id))
                .map(getRowMapper())
                .one()
                .doOnNext(entity ->
                        log.debug("Found {} with id: {}", entityClass.getSimpleName(), id)
                );
    }


    @Override
    public Mono<Long> count() {
        String sql = String.format("SELECT COUNT(*) FROM %s", tableName);

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Mono<Boolean> existsById(ID id) {
        String sql = String.format(
                "SELECT EXISTS(SELECT 1 FROM %s WHERE id = :id)",
                tableName
        );

        return databaseClient.sql(sql)
                .bind("id", convertIdToDatabase(id))
                .map(row -> row.get(0, Boolean.class))
                .one();
    }

    @Override
    public Mono<Void> deleteById(ID id) {
        String sql = String.format("DELETE FROM %s WHERE id = :id", tableName);

        return databaseClient.sql(sql)
                .bind("id", convertIdToDatabase(id))
                .fetch()
                .rowsUpdated()
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.debug("Deleted {} with id: {}",
                                entityClass.getSimpleName(), id);
                    } else {
                        log.warn("No {} found with id: {} to delete",
                                entityClass.getSimpleName(), id);
                    }
                })
                .then();
    }


    protected Mono<T> insert(T entity) {
        Map<String, Object> values = mapEntityToColumns(entity);
        values.put("created_at", Instant.now());
        values.put("updated_at", Instant.now());
        values.put("version", 1L);

        String columns = String.join(", ", values.keySet());
        String placeholders = values.keySet().stream()
                .map(key -> ":" + key)
                .collect(Collectors.joining(", "));

        String sql = String.format(
                "INSERT INTO %s (%s) VALUES (%s) RETURNING *",
                tableName, columns, placeholders
        );

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            spec = bindValue(spec, entry.getKey(), entry.getValue());
        }

        return spec
                .map(getRowMapper())
                .one()
                .switchIfEmpty(Mono.error(
                        new RuntimeException("Failed to insert " + entityClass.getSimpleName())
                ));
    }

    protected Mono<T> update(T entity) {
        Map<String, Object> values = mapEntityToColumns(entity);
        values.put("updated_at", Instant.now());
        values.put("version", entity.getVersion() + 1);

        String setClause = values.keySet().stream()
                .filter(o -> !o.equals("id"))
                .map(o -> o + " = :" + o)
                .collect(Collectors.joining(", "));
//
//        String sql = String.format(
//                "UPDATE %s SET %s WHERE id = :id AND version = :old_version RETURNING *",
//                tableName, setClause
//        );

        String sql = String.format(
                "UPDATE %s SET %s WHERE id = :id RETURNING *",
                tableName, setClause
        );

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("id", convertIdToDatabase(entity.getId()));
//                .bind("old_version", entity.getVersion());
//
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!entry.getKey().equals("id")) {
                spec = bindValue(spec, entry.getKey(), entry.getValue());
            }
        }

        return spec.map(getRowMapper())
                .one()
                .switchIfEmpty(Mono.defer(() -> {
                    String msg = "Version conflict for " + entityClass.getSimpleName()
                            + " with id: " + entity.getId();
                    log.error(msg);
                    return Mono.error(new OptimisticLockingFailureException(msg));
                }));
    }


    protected DatabaseClient.GenericExecuteSpec bindValue(
            DatabaseClient.GenericExecuteSpec spec,
            String name,
            Object value
    ) {
        if (value == null) {
            return spec.bindNull(name, Object.class);
        }

        if (value instanceof Instant) {
            return spec.bind(name, OffsetDateTime.ofInstant((Instant) value, ZoneOffset.UTC));
        }

        if (value instanceof Enum<?>) {
            return spec.bind(name, ((Enum<?>) value).name());
        }

        if (value instanceof java.util.UUID) {
            return spec.bind(name, value);
        }

        if (value instanceof Number || value instanceof String || value instanceof Boolean) {
            return spec.bind(name, value);
        }

        return spec.bind(name, value.toString());
    }



    protected String getOrderByClause(Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return "ORDER BY created_at DESC";
        }
        return "ORDER BY " + pageable.getSort().stream()
                .map(order -> order.getProperty().toLowerCase() + " " + order.getDirection().name())
                .collect(Collectors.joining(", "));
    }




    protected abstract String convertIdToDatabase(ID id);

    protected abstract BiFunction<Row, RowMetadata, T> getRowMapper();

    protected abstract Map<String, Object> mapEntityToColumns(T entity);
}
