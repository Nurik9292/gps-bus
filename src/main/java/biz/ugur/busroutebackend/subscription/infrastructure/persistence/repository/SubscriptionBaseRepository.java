package biz.ugur.busroutebackend.subscription.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.valueobjects.SubscriptionId;
import biz.ugur.busroutebackend.subscription.infrastructure.mapper.SubscriptionMapper;
import biz.ugur.busroutebackend.subscription.infrastructure.persistence.entity.SubscriptionEntity;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public abstract class SubscriptionBaseRepository extends BaseR2dbcRepository<Subscription, SubscriptionId> {

    protected static final String SELECT_COLUMNS = String.join(", ",
            "id", "client_id", "period", "status", "payment_id",
            "amount_minor", "currency",
            "started_at", "expires_at", "cancelled_at", "cancellation_reason",
            "created_at", "updated_at", "version"
    );

    protected SubscriptionBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "client_subscriptions", Subscription.class);
    }

    @Override
    protected String selectColumns() {
        return SELECT_COLUMNS;
    }

    @Override
    protected String convertIdToDatabase(SubscriptionId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Subscription> getRowMapper() {
        return this::mapRow;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Subscription subscription) {
        SubscriptionEntity e = SubscriptionMapper.toEntity(subscription);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", e.getId());
        columns.put("client_id", e.getClientId());
        columns.put("period", e.getPeriod());
        columns.put("status", e.getStatus());
        columns.put("payment_id", e.getPaymentId());
        columns.put("amount_minor", e.getAmountMinor());
        columns.put("currency", e.getCurrency());
        columns.put("started_at", e.getStartedAt());
        columns.put("expires_at", e.getExpiresAt());
        columns.put("cancelled_at", e.getCancelledAt());
        columns.put("cancellation_reason", e.getCancellationReason());
        columns.put("created_at", e.getCreatedAt());
        columns.put("updated_at", e.getUpdatedAt());
        columns.put("version", e.getVersion());
        return columns;
    }

    private Subscription mapRow(Row row, RowMetadata meta) {
        return SubscriptionMapper.toDomain(SubscriptionEntity.builder()
                .id(row.get("id", String.class))
                .clientId(row.get("client_id", String.class))
                .period(row.get("period", String.class))
                .status(row.get("status", String.class))
                .paymentId(row.get("payment_id", String.class))
                .amountMinor(row.get("amount_minor", Long.class))
                .currency(row.get("currency", String.class))
                .startedAt(row.get("started_at", LocalDateTime.class))
                .expiresAt(row.get("expires_at", LocalDateTime.class))
                .cancelledAt(row.get("cancelled_at", LocalDateTime.class))
                .cancellationReason(row.get("cancellation_reason", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }
}
