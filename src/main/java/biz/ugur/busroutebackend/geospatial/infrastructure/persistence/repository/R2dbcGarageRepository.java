package biz.ugur.busroutebackend.geospatial.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.geospatial.domain.model.Garage;
import biz.ugur.busroutebackend.geospatial.domain.repository.GarageRepository;
import biz.ugur.busroutebackend.geospatial.domain.valueobject.GarageId;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.infrastructure.mapper.GarageEntityMapper;
import biz.ugur.busroutebackend.geospatial.infrastructure.persistence.entity.GarageEntity;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;


@Repository
@Slf4j
@Transactional(readOnly = true)
public class R2dbcGarageRepository extends BaseR2dbcRepository<Garage, GarageId> implements GarageRepository {

    private static final String GARAGE_COLUMNS = """
            id, name, name_tm, name_en, latitude, longitude, radius_meters,
            city_id, is_active, created_at, updated_at, version,
            ST_AsText(boundary) AS boundary_wkt
            """;

    private final GarageEntityMapper entityMapper;

    public R2dbcGarageRepository(DatabaseClient databaseClient, GarageEntityMapper entityMapper) {
        super(databaseClient, "garages", Garage.class);
        this.entityMapper = entityMapper;
    }

    @Override
    protected String convertIdToDatabase(GarageId id) {
        return id.getValue().toString();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Garage> getRowMapper() {
        return this::mapRowToGarage;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Garage entity) {
        GarageEntity persistenceEntity = entityMapper.toEntity(entity);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", persistenceEntity.getId());
        columns.put("name", persistenceEntity.getName());
        columns.put("name_tm", persistenceEntity.getNameTm());
        columns.put("name_en", persistenceEntity.getNameEn());
        columns.put("latitude", persistenceEntity.getLatitude());
        columns.put("longitude", persistenceEntity.getLongitude());
        columns.put("radius_meters", persistenceEntity.getRadiusMeters());
        columns.put("boundary_wkt", persistenceEntity.getBoundaryWkt());
        columns.put("city_id", persistenceEntity.getCityId());
        columns.put("is_active", persistenceEntity.getIsActive());
        return columns;
    }

    @Override
    protected Mono<Garage> insert(Garage entity) {
        Map<String, Object> values = mapEntityToColumns(entity);
        values.put("created_at", LocalDateTime.now());
        values.put("updated_at", LocalDateTime.now());
        values.put("version", 1L);

        String sql = """
            INSERT INTO garages (
                id, name, name_tm, name_en, latitude, longitude,
                radius_meters, boundary, city_id, is_active, created_at, updated_at, version
            ) VALUES (
                :id, :name, :name_tm, :name_en, :latitude, :longitude,
                :radius_meters,
                CASE WHEN :boundary_wkt IS NOT NULL THEN ST_GeogFromText(:boundary_wkt) ELSE NULL END,
                :city_id, :is_active, :created_at, :updated_at, :version
            ) RETURNING *, ST_AsText(boundary) as boundary_wkt
            """;

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            spec = bindValue(spec, entry.getKey(), entry.getValue());
        }

        return spec
                .map(getRowMapper())
                .one()
                .switchIfEmpty(Mono.error(
                        new RuntimeException("Failed to insert " + entityClass.getSimpleName())
                ))
                .doOnSuccess(g -> log.info("Successfully inserted garage: {} (id: {})",
                        g.getName(), g.getId()));
    }

    @Override
    protected Mono<Garage> update(Garage entity) {
        Map<String, Object> values = mapEntityToColumns(entity);
        values.put("updated_at", LocalDateTime.now());
        values.put("version", entity.getVersion() + 1);

        String sql = """
            UPDATE garages SET
                name = :name,
                name_tm = :name_tm,
                name_en = :name_en,
                latitude = :latitude,
                longitude = :longitude,
                radius_meters = :radius_meters,
                boundary = CASE WHEN :boundary_wkt IS NOT NULL THEN ST_GeogFromText(:boundary_wkt) ELSE NULL END,
                city_id = :city_id,
                is_active = :is_active,
                updated_at = :updated_at,
                version = :version
            WHERE id = :id AND version = :old_version
            RETURNING *, ST_AsText(boundary) as boundary_wkt
            """;

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("id", entity.getId().getValue().toString())
                .bind("old_version", entity.getVersion());

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!entry.getKey().equals("id")) {
                spec = bindValue(spec, entry.getKey(), entry.getValue());
            }
        }

        return spec.map(getRowMapper())
                .one()
                .switchIfEmpty(Mono.defer(() -> {
                    String msg = String.format(
                            "Optimistic lock failure for %s with id: %s. " +
                            "Entity was modified by another transaction (expected version: %d)",
                            entityClass.getSimpleName(),
                            entity.getId(),
                            entity.getVersion()
                    );
                    log.error(msg);
                    return Mono.error(new org.springframework.dao.OptimisticLockingFailureException(msg));
                }))
                .doOnSuccess(g -> log.debug("Successfully updated garage: {}", g.getName()));
    }

    @Override
    public Flux<Garage> findAllActive() {
        String sql = "SELECT " + GARAGE_COLUMNS + " FROM garages WHERE is_active = true ORDER BY name";

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all()
                .doOnNext(g -> log.debug("Found active garage: {}", g.getName()));
    }

    @Override
    public Flux<Garage> findByCityId(String cityId) {
        String sql = "SELECT " + GARAGE_COLUMNS + " FROM garages WHERE city_id = :cityId AND is_active = true ORDER BY name";

        return databaseClient.sql(sql)
                .bind("cityId", cityId)
                .map(getRowMapper())
                .all()
                .doOnNext(g -> log.debug("Found garage in city {}: {}", cityId, g.getName()));
    }

    @Override
    public Mono<Optional<Garage>> findGarageContainingPosition(Coordinates position) {
        String sql = String.format("""
            SELECT %s
            FROM garages
            WHERE is_active = true
            AND (
                CASE
                    WHEN boundary IS NOT NULL THEN ST_Contains(boundary::geometry, ST_SetSRID(ST_Point(:lon, :lat), 4326))
                    ELSE ST_DWithin(
                        ST_SetSRID(ST_Point(longitude, latitude), 4326)::geography,
                        ST_SetSRID(ST_Point(:lon, :lat), 4326)::geography,
                        radius_meters
                    )
                END
            )
            LIMIT 1
            """, GARAGE_COLUMNS);

        return databaseClient.sql(sql)
                .bind("lon", position.getLongitudeAsDouble())
                .bind("lat", position.getLatitudeAsDouble())
                .map(getRowMapper())
                .one()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .doOnNext(result -> {
                    if (result.isPresent()) {
                        log.debug("Found garage containing position ({}, {}): {}",
                                position.getLatitudeAsDouble(),
                                position.getLongitudeAsDouble(),
                                result.get().getName());
                    }
                });
    }

    @Override
    public Mono<Boolean> isPositionInsideGarage(GarageId garageId, Coordinates position) {
        String sql = """
            SELECT CASE
                WHEN boundary IS NOT NULL THEN ST_Contains(boundary::geometry, ST_SetSRID(ST_Point(:lon, :lat), 4326))
                ELSE ST_DWithin(
                    ST_SetSRID(ST_Point(longitude, latitude), 4326)::geography,
                    ST_SetSRID(ST_Point(:lon, :lat), 4326)::geography,
                    radius_meters
                )
            END as is_inside
            FROM garages
            WHERE id = :garageId AND is_active = true
            """;

        return databaseClient.sql(sql)
                .bind("garageId", garageId.getValue().toString())
                .bind("lon", position.getLongitudeAsDouble())
                .bind("lat", position.getLatitudeAsDouble())
                .map(row -> row.get("is_inside", Boolean.class))
                .one()
                .defaultIfEmpty(false)
                .doOnNext(result -> log.debug("Position ({}, {}) inside garage {}: {}",
                        position.getLatitudeAsDouble(),
                        position.getLongitudeAsDouble(),
                        garageId.getValue(),
                        result));
    }

    @Override
    public Mono<Boolean> hasPositionExitedGarage(GarageId garageId, Coordinates position, int bufferMeters) {
        String sql = """
            SELECT CASE
                WHEN boundary IS NOT NULL THEN NOT ST_DWithin(
                    boundary,
                    ST_SetSRID(ST_Point(:lon, :lat), 4326)::geography,
                    :buffer
                )
                ELSE NOT ST_DWithin(
                    ST_SetSRID(ST_Point(longitude, latitude), 4326)::geography,
                    ST_SetSRID(ST_Point(:lon, :lat), 4326)::geography,
                    radius_meters + :buffer
                )
            END as has_exited
            FROM garages
            WHERE id = :garageId AND is_active = true
            """;

        return databaseClient.sql(sql)
                .bind("garageId", garageId.getValue().toString())
                .bind("lon", position.getLongitudeAsDouble())
                .bind("lat", position.getLatitudeAsDouble())
                .bind("buffer", bufferMeters)
                .map(row -> row.get("has_exited", Boolean.class))
                .one()
                .defaultIfEmpty(true)
                .doOnNext(result -> log.debug("Position ({}, {}) exited garage {} (buffer {}m): {}",
                        position.getLatitudeAsDouble(),
                        position.getLongitudeAsDouble(),
                        garageId.getValue(),
                        bufferMeters,
                        result));
    }

    private Garage mapRowToGarage(Row row, RowMetadata metadata) {
        GarageEntity entity = GarageEntity.builder()
                .id(row.get("id", String.class))
                .name(row.get("name", String.class))
                .nameTm(row.get("name_tm", String.class))
                .nameEn(row.get("name_en", String.class))
                .latitude(row.get("latitude", Double.class))
                .longitude(row.get("longitude", Double.class))
                .radiusMeters(row.get("radius_meters", Integer.class))
                .boundaryWkt(safeGet(row, "boundary_wkt", String.class, null))
                .cityId(row.get("city_id", String.class))
                .isActive(row.get("is_active", Boolean.class))
                .createdAt(safeGet(row, "created_at", LocalDateTime.class, null))
                .updatedAt(safeGet(row, "updated_at", LocalDateTime.class, null))
                .version(safeGet(row, "version", Long.class, 0L))
                .build();

        return entityMapper.toDomain(entity);
    }

    private <T> T safeGet(Row row, String columnName, Class<T> type, T defaultValue) {
        try {
            T value = row.get(columnName, type);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            log.debug("Column '{}' not found, using default value: {}", columnName, defaultValue);
            return defaultValue;
        }
    }
}
