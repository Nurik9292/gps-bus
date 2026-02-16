package biz.ugur.busroutebackend.place.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.place.domain.model.PlaceAlias;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceAliasId;
import biz.ugur.busroutebackend.place.infrastructure.mapper.PlaceAliasEntityMapper;
import biz.ugur.busroutebackend.place.infrastructure.persistence.entity.PlaceAliasEntity;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Repository
public class R2dbcPlaceAliasRepository extends BaseR2dbcRepository<PlaceAlias, PlaceAliasId>
        implements PlaceAliasRepository {

    public R2dbcPlaceAliasRepository(DatabaseClient databaseClient) {
        super(databaseClient, "place_aliases", PlaceAlias.class);
    }

    @Override
    protected String convertIdToDatabase(PlaceAliasId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, PlaceAlias> getRowMapper() {
        return this::mapRow;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(PlaceAlias alias) {
        PlaceAliasEntity entity = PlaceAliasEntityMapper.toEntity(alias);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", entity.getId());
        columns.put("place_id", entity.getPlaceId());
        columns.put("alias", entity.getAlias());
        columns.put("language", entity.getLanguage());
        columns.put("version", entity.getVersion());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        return columns;
    }

    @Override
    public Flux<PlaceAlias> findByPlaceId(String placeId) {
        String sql = "SELECT * FROM place_aliases WHERE place_id = :placeId ORDER BY alias";
        return databaseClient.sql(sql)
                .bind("placeId", placeId)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Void> deleteByPlaceId(String placeId) {
        String sql = "DELETE FROM place_aliases WHERE place_id = :placeId";
        return databaseClient.sql(sql)
                .bind("placeId", placeId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private PlaceAlias mapRow(Row row, RowMetadata metadata) {
        return PlaceAliasEntityMapper.toDomain(PlaceAliasEntity.builder()
                .id(row.get("id", String.class))
                .placeId(row.get("place_id", String.class))
                .alias(row.get("alias", String.class))
                .language(row.get("language", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }
}
