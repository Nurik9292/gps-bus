package biz.ugur.busroutebackend.advertising.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.advertising.domain.model.AdTariff;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.advertising.infrastructure.mapper.AdTariffMapper;
import biz.ugur.busroutebackend.advertising.infrastructure.persistence.entity.AdTariffEntity;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public abstract class AdTariffBaseRepository extends BaseR2dbcRepository<AdTariff, TariffId> {

    protected AdTariffBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "ad_tariffs", AdTariff.class);
    }

    @Override protected String convertIdToDatabase(TariffId id) { return id.getValue(); }

    @Override protected BiFunction<Row, RowMetadata, AdTariff> getRowMapper() { return this::mapRow; }

    @Override
    protected Map<String, Object> mapEntityToColumns(AdTariff tariff) {
        AdTariffEntity e = AdTariffMapper.toEntity(tariff);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", e.getId());
        columns.put("name", e.getName());
        columns.put("description", e.getDescription());
        columns.put("placement_type", e.getPlacementType());
        columns.put("period", e.getPeriod());
        columns.put("price_amount", e.getPriceAmount());
        columns.put("currency", e.getCurrency());
        columns.put("max_impressions", e.getMaxImpressions());
        columns.put("max_clicks", e.getMaxClicks());
        columns.put("daily_impression_cap", e.getDailyImpressionCap());
        columns.put("is_active", e.getIsActive());
        columns.put("display_order", e.getDisplayOrder());
        columns.put("created_at", e.getCreatedAt());
        columns.put("updated_at", e.getUpdatedAt());
        columns.put("version", e.getVersion());
        return columns;
    }

    private AdTariff mapRow(Row row, RowMetadata meta) {
        return AdTariffMapper.toDomain(AdTariffEntity.builder()
                .id(row.get("id", String.class))
                .name(row.get("name", String.class))
                .description(row.get("description", String.class))
                .placementType(row.get("placement_type", String.class))
                .period(row.get("period", String.class))
                .priceAmount(row.get("price_amount", Long.class))
                .currency(row.get("currency", String.class))
                .maxImpressions(row.get("max_impressions", Integer.class))
                .maxClicks(row.get("max_clicks", Integer.class))
                .dailyImpressionCap(row.get("daily_impression_cap", Integer.class))
                .isActive(row.get("is_active", Boolean.class))
                .displayOrder(row.get("display_order", Integer.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }
}
