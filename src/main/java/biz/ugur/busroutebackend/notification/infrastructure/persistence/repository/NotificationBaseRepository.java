package biz.ugur.busroutebackend.notification.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationId;
import biz.ugur.busroutebackend.notification.infrastructure.mapper.NotificationMapper;
import biz.ugur.busroutebackend.notification.infrastructure.persistence.entity.NotificationEntity;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.BiFunction;

@Repository
public abstract class NotificationBaseRepository extends BaseR2dbcRepository<Notification, NotificationId> {

    protected NotificationBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "notifications", Notification.class);
    }


    @Override
    protected String convertIdToDatabase(NotificationId notificationId) {
        return notificationId.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Notification> getRowMapper() {
        return this::mapRowToNotification;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Notification notification) {
        NotificationEntity entity = NotificationMapper.toEntity(notification);
        Map<String, Object> columns = new java.util.HashMap<>();
        columns.put("id", entity.getId());
        columns.put("title", entity.getTitle());
        columns.put("content", entity.getContent());
        columns.put("is_active", entity.getIsActive());
        columns.put("display_order", entity.getDisplayOrder());
        columns.put("version", entity.getVersion());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        return columns;
    }

    private Notification mapRowToNotification(Row row, RowMetadata metadata) {
        return NotificationMapper.toDomain(NotificationEntity.builder()
                .id(row.get("id", String.class))
                .title(row.get("title", String.class))
                .content(row.get("content", String.class))
                .isActive(row.get("is_active", Boolean.class))
                .displayOrder(row.get("display_order", Integer.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }


    public Flux<Notification> findBySpecification(Specification<Notification> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT * FROM notifications WHERE %s ORDER BY display_order ASC, created_at DESC",
            criteria.getWhereClause()
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql);

        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(getRowMapper())
                .all();
    }


    public Flux<Notification> findBySpecification(Specification<Notification> specification, Pageable pageable) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT * FROM notifications WHERE %s %s LIMIT :limit OFFSET :offset",
            criteria.getWhereClause(),
            getOrderByClause(pageable)
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset());

        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(getRowMapper())
                .all();
    }


    public Mono<Long> countBySpecification(Specification<Notification> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT COUNT(*) FROM notifications WHERE %s",
            criteria.getWhereClause()
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql);

        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(row -> row.get(0, Long.class))
                .one();
    }
}
