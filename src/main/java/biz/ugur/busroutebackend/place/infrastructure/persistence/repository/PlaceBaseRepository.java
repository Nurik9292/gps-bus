package biz.ugur.busroutebackend.place.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.place.domain.model.Place;
import biz.ugur.busroutebackend.place.domain.model.PlaceCategory;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceId;
import biz.ugur.busroutebackend.place.infrastructure.mapper.PlaceEntityMapper;
import biz.ugur.busroutebackend.place.infrastructure.persistence.entity.PlaceEntity;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public abstract class PlaceBaseRepository extends BaseR2dbcRepository<Place, PlaceId> {

    protected static final String SELECT_COLUMNS = String.join(", ",
            "id", "name", "name_en", "name_tm", "description", "address",
            "category", "city_id", "latitude", "longitude", "is_active",
            "version", "created_at", "updated_at"
    );

    protected PlaceBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "places", Place.class);
    }

    @Override
    protected String selectColumns() {
        return SELECT_COLUMNS;
    }

    @Override
    protected String convertIdToDatabase(PlaceId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Place> getRowMapper() {
        return this::mapRowToPlace;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Place place) {
        PlaceEntity entity = PlaceEntityMapper.toEntity(place);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", entity.getId());
        columns.put("name", entity.getName());
        columns.put("name_en", entity.getNameEn());
        columns.put("name_tm", entity.getNameTm());
        columns.put("description", entity.getDescription());
        columns.put("address", entity.getAddress());
        columns.put("category", entity.getCategory());
        columns.put("city_id", entity.getCityId());
        columns.put("latitude", entity.getLatitude());
        columns.put("longitude", entity.getLongitude());
        columns.put("is_active", entity.getIsActive());
        columns.put("version", entity.getVersion());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        return columns;
    }

    private Place mapRowToPlace(Row row, RowMetadata metadata) {
        return PlaceEntityMapper.toDomain(PlaceEntity.builder()
                .id(row.get("id", String.class))
                .name(row.get("name", String.class))
                .nameEn(row.get("name_en", String.class))
                .nameTm(row.get("name_tm", String.class))
                .description(row.get("description", String.class))
                .address(row.get("address", String.class))
                .category(row.get("category", String.class))
                .cityId(row.get("city_id", String.class))
                .latitude(row.get("latitude", BigDecimal.class))
                .longitude(row.get("longitude", BigDecimal.class))
                .isActive(row.get("is_active", Boolean.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }
}
