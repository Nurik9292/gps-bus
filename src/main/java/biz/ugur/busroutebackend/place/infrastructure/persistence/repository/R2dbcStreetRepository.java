package biz.ugur.busroutebackend.place.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.place.domain.model.Street;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetId;
import biz.ugur.busroutebackend.place.infrastructure.mapper.StreetEntityMapper;
import biz.ugur.busroutebackend.place.infrastructure.persistence.entity.StreetEntity;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Repository
public class R2dbcStreetRepository extends BaseR2dbcRepository<Street, StreetId>
        implements StreetRepository {

    private static final String SELECT_COLUMNS = String.join(", ",
            "id", "name", "name_en", "name_tm", "city_id", "is_active",
            "version", "created_at", "updated_at"
    );

    public R2dbcStreetRepository(DatabaseClient databaseClient) {
        super(databaseClient, "streets", Street.class);
    }

    @Override
    protected String selectColumns() {
        return SELECT_COLUMNS;
    }

    @Override
    protected String convertIdToDatabase(StreetId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Street> getRowMapper() {
        return this::mapRow;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Street street) {
        StreetEntity entity = StreetEntityMapper.toEntity(street);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", entity.getId());
        columns.put("name", entity.getName());
        columns.put("name_en", entity.getNameEn());
        columns.put("name_tm", entity.getNameTm());
        columns.put("city_id", entity.getCityId());
        columns.put("is_active", entity.getIsActive());
        columns.put("version", entity.getVersion());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        return columns;
    }

    @Override
    public Flux<Street> findByCityId(String cityId) {
        String sql = String.format(
                "SELECT %s FROM streets WHERE city_id = :cityId AND is_active = true ORDER BY name",
                selectColumns()
        );
        return databaseClient.sql(sql)
                .bind("cityId", cityId)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<Street> findByCityId(String cityId, Pageable pageable) {
        String sql = String.format(
                "SELECT %s FROM streets WHERE city_id = :cityId %s LIMIT :limit OFFSET :offset",
                selectColumns(),
                getOrderByClause(pageable)
        );
        return databaseClient.sql(sql)
                .bind("cityId", cityId)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Long> countByCityId(String cityId) {
        String sql = "SELECT COUNT(*) FROM streets WHERE city_id = :cityId";
        return databaseClient.sql(sql)
                .bind("cityId", cityId)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Mono<Boolean> existsByNameAndCityId(String name, String cityId) {
        String sql = "SELECT EXISTS(SELECT 1 FROM streets WHERE LOWER(name) = LOWER(:name) AND city_id = :cityId)";
        return databaseClient.sql(sql)
                .bind("name", name)
                .bind("cityId", cityId)
                .map(row -> row.get(0, Boolean.class))
                .one();
    }

    private Street mapRow(Row row, RowMetadata metadata) {
        return StreetEntityMapper.toDomain(StreetEntity.builder()
                .id(row.get("id", String.class))
                .name(row.get("name", String.class))
                .nameEn(row.get("name_en", String.class))
                .nameTm(row.get("name_tm", String.class))
                .cityId(row.get("city_id", String.class))
                .isActive(row.get("is_active", Boolean.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }
}
