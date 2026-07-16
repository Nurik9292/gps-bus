package biz.ugur.busroutebackend.place.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.place.domain.model.StreetAlias;
import biz.ugur.busroutebackend.place.domain.repository.StreetAliasRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetAliasId;
import biz.ugur.busroutebackend.place.infrastructure.mapper.StreetAliasEntityMapper;
import biz.ugur.busroutebackend.place.infrastructure.persistence.entity.StreetAliasEntity;
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
public class R2dbcStreetAliasRepository extends BaseR2dbcRepository<StreetAlias, StreetAliasId>
        implements StreetAliasRepository {

    private static final String SELECT_COLUMNS = String.join(", ",
            "id", "street_id", "alias", "language",
            "version", "created_at", "updated_at"
    );

    public R2dbcStreetAliasRepository(DatabaseClient databaseClient) {
        super(databaseClient, "street_aliases", StreetAlias.class);
    }

    @Override
    protected String selectColumns() {
        return SELECT_COLUMNS;
    }

    @Override
    protected String convertIdToDatabase(StreetAliasId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, StreetAlias> getRowMapper() {
        return this::mapRow;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(StreetAlias alias) {
        StreetAliasEntity entity = StreetAliasEntityMapper.toEntity(alias);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", entity.getId());
        columns.put("street_id", entity.getStreetId());
        columns.put("alias", entity.getAlias());
        columns.put("language", entity.getLanguage());
        columns.put("version", entity.getVersion());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        return columns;
    }

    @Override
    public Flux<StreetAlias> findByStreetId(String streetId) {
        String sql = String.format(
                "SELECT %s FROM street_aliases WHERE street_id = :streetId ORDER BY alias",
                selectColumns()
        );
        return databaseClient.sql(sql)
                .bind("streetId", streetId)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Void> deleteByStreetId(String streetId) {
        String sql = "DELETE FROM street_aliases WHERE street_id = :streetId";
        return databaseClient.sql(sql)
                .bind("streetId", streetId)
                .fetch()
                .rowsUpdated()
                .then();
    }


    private StreetAlias mapRow(Row row, RowMetadata metadata) {
        return StreetAliasEntityMapper.toDomain(StreetAliasEntity.builder()
                .id(row.get("id", String.class))
                .streetId(row.get("street_id", String.class))
                .alias(row.get("alias", String.class))
                .language(row.get("language", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }
}
