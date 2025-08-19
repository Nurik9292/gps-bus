package biz.ugur.busroutebackend.shared.infrastructure.persistence;

import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseR2dbcRepository<T extends AggregateRoot<T, ID>, ID> implements BaseRepository<T, ID> {

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
                .flatMap(existing -> update(entity))
                .switchIfEmpty(insert(entity));
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

        String sql = String.format(
                "UPDATE %s SET %s WHERE id = :id AND version = :old_version RETURNING *",
                tableName, setClause
        );

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("id", entity.getId())
                .bind("old_version", entity.getVersion());

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
        return spec.bind(name, value);
    }

    protected String buildOrderBy(Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return "created_at DESC";
        }
        return pageable.getSort().stream()
                .map(order -> order.getProperty() + " " + order.getDirection().name())
                .collect(Collectors.joining(", "));
    }

    protected abstract BiFunction<Row, RowMetadata, T> getRowMapper();

    protected abstract Map<String, Object> mapEntityToColumns(T entity);
}
