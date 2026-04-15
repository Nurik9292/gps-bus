package biz.ugur.busroutebackend.advertising.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.infrastructure.mapper.AdPlacementMapper;
import biz.ugur.busroutebackend.advertising.infrastructure.persistence.entity.AdPlacementEntity;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public abstract class AdPlacementBaseRepository extends BaseR2dbcRepository<AdPlacement, PlacementId> {

    protected AdPlacementBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "ad_placements", AdPlacement.class);
    }

    @Override protected String convertIdToDatabase(PlacementId id) { return id.getValue(); }

    @Override protected BiFunction<Row, RowMetadata, AdPlacement> getRowMapper() { return this::mapRow; }

    @Override
    protected Map<String, Object> mapEntityToColumns(AdPlacement placement) {
        AdPlacementEntity e = AdPlacementMapper.toEntity(placement);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", e.getId());
        columns.put("business_id", e.getBusinessId());
        columns.put("tariff_id", e.getTariffId());
        columns.put("placement_type", e.getPlacementType());
        columns.put("status", e.getStatus());
        columns.put("title", e.getTitle());
        columns.put("content", e.getContent());
        columns.put("image_url", e.getImageUrl());
        columns.put("target_url", e.getTargetUrl());
        columns.put("cta_text", e.getCtaText());
        columns.put("starts_at", e.getStartsAt());
        columns.put("ends_at", e.getEndsAt());
        columns.put("impressions_count", e.getImpressionsCount());
        columns.put("clicks_count", e.getClicksCount());
        columns.put("display_contexts", e.getDisplayContexts());
        columns.put("display_order", e.getDisplayOrder());
        columns.put("created_at", e.getCreatedAt());
        columns.put("updated_at", e.getUpdatedAt());
        columns.put("version", e.getVersion());
        return columns;
    }

    private AdPlacement mapRow(Row row, RowMetadata meta) {
        return AdPlacementMapper.toDomain(AdPlacementEntity.builder()
                .id(row.get("id", String.class))
                .businessId(row.get("business_id", String.class))
                .tariffId(row.get("tariff_id", String.class))
                .placementType(row.get("placement_type", String.class))
                .status(row.get("status", String.class))
                .title(row.get("title", String.class))
                .content(row.get("content", String.class))
                .imageUrl(row.get("image_url", String.class))
                .targetUrl(row.get("target_url", String.class))
                .ctaText(row.get("cta_text", String.class))
                .startsAt(row.get("starts_at", LocalDateTime.class))
                .endsAt(row.get("ends_at", LocalDateTime.class))
                .impressionsCount(row.get("impressions_count", Long.class))
                .clicksCount(row.get("clicks_count", Long.class))
                .displayContexts(row.get("display_contexts", String.class))
                .displayOrder(row.get("display_order", Integer.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }
}
