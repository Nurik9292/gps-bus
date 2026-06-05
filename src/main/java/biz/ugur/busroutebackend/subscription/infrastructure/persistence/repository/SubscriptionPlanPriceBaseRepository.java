package biz.ugur.busroutebackend.subscription.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.model.SubscriptionPlanPrice;
import biz.ugur.busroutebackend.subscription.infrastructure.mapper.SubscriptionPlanPriceMapper;
import biz.ugur.busroutebackend.subscription.infrastructure.persistence.entity.SubscriptionPlanPriceEntity;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public abstract class SubscriptionPlanPriceBaseRepository
        extends BaseR2dbcRepository<SubscriptionPlanPrice, SubscriptionPeriod> {

    protected static final String SELECT_COLUMNS = String.join(", ",
            "id", "amount_minor", "currency", "updated_by",
            "created_at", "updated_at", "version"
    );

    protected SubscriptionPlanPriceBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "subscription_plan_prices", SubscriptionPlanPrice.class);
    }

    @Override
    protected String selectColumns() {
        return SELECT_COLUMNS;
    }

    @Override
    protected String convertIdToDatabase(SubscriptionPeriod id) {
        return id.name();
    }

    @Override
    protected BiFunction<Row, RowMetadata, SubscriptionPlanPrice> getRowMapper() {
        return this::mapRow;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(SubscriptionPlanPrice price) {
        SubscriptionPlanPriceEntity e = SubscriptionPlanPriceMapper.toEntity(price);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", e.getId());
        columns.put("amount_minor", e.getAmountMinor());
        columns.put("currency", e.getCurrency());
        columns.put("updated_by", e.getUpdatedBy());
        columns.put("created_at", e.getCreatedAt());
        columns.put("updated_at", e.getUpdatedAt());
        columns.put("version", e.getVersion());
        return columns;
    }

    private SubscriptionPlanPrice mapRow(Row row, RowMetadata meta) {
        return SubscriptionPlanPriceMapper.toDomain(SubscriptionPlanPriceEntity.builder()
                .id(row.get("id", String.class))
                .amountMinor(row.get("amount_minor", Long.class))
                .currency(row.get("currency", String.class))
                .updatedBy(row.get("updated_by", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }
}
