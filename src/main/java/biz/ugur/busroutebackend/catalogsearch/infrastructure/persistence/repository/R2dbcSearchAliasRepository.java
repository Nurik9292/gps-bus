package biz.ugur.busroutebackend.catalogsearch.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.catalogsearch.domain.model.AliasCollision;
import biz.ugur.busroutebackend.catalogsearch.domain.model.AliasSource;
import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchAlias;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchAliasView;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

@Repository
public class R2dbcSearchAliasRepository implements SearchAliasRepository {

    private static final String ALIAS_COLUMNS =
            "sa.id, sa.object_kind, sa.object_id, sa.alias_raw, sa.alias_norm, " +
            "sa.weight, sa.source, sa.created_by, sa.created_at, sa.updated_at";

    private static final String OBJECT_TITLE_JOIN =
            " LEFT JOIN bus_stops bs ON sa.object_kind = 'STOP' AND bs.id = sa.object_id" +
            " LEFT JOIN bus_routes br ON sa.object_kind = 'ROUTE' AND br.id = sa.object_id";

    private static final String OBJECT_TITLE_EXPR = "COALESCE(bs.stop_name, br.route_number)";

    private final DatabaseClient databaseClient;

    public R2dbcSearchAliasRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<SearchAlias> save(SearchAlias alias) {
        if (alias.getId() == null) {
            DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                    INSERT INTO search_alias
                        (object_kind, object_id, alias_raw, alias_norm, weight, source, created_by, created_at, updated_at)
                    VALUES (:kind, :objectId, :aliasRaw, :aliasNorm, :weight, :source, :createdBy, :createdAt, :updatedAt)
                    RETURNING id
                    """)
                    .bind("kind", alias.getObjectKind().name())
                    .bind("objectId", alias.getObjectId())
                    .bind("aliasRaw", alias.getAliasRaw())
                    .bind("aliasNorm", alias.getAliasNorm())
                    .bind("weight", alias.getWeight())
                    .bind("source", alias.getSource().name())
                    .bind("createdAt", alias.getCreatedAt())
                    .bind("updatedAt", alias.getUpdatedAt());
            spec = alias.getCreatedBy() == null
                    ? spec.bindNull("createdBy", String.class)
                    : spec.bind("createdBy", alias.getCreatedBy());
            return spec.map(row -> row.get("id", Long.class))
                    .one()
                    .map(id -> alias.toBuilder().id(id).build());
        }
        return databaseClient.sql("""
                UPDATE search_alias
                SET weight = :weight, source = :source, updated_at = :updatedAt
                WHERE id = :id
                """)
                .bind("weight", alias.getWeight())
                .bind("source", alias.getSource().name())
                .bind("updatedAt", alias.getUpdatedAt())
                .bind("id", alias.getId())
                .fetch()
                .rowsUpdated()
                .thenReturn(alias);
    }

    @Override
    public Mono<SearchAlias> findById(Long id) {
        return databaseClient.sql("SELECT " + ALIAS_COLUMNS + " FROM search_alias sa WHERE sa.id = :id")
                .bind("id", id)
                .map(R2dbcSearchAliasRepository::mapAlias)
                .one();
    }

    @Override
    public Mono<Void> deleteById(Long id) {
        return databaseClient.sql("DELETE FROM search_alias WHERE id = :id")
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Flux<SearchAliasView> findByObject(CatalogObjectKind kind, String objectId) {
        return databaseClient.sql("SELECT " + ALIAS_COLUMNS + ", " + OBJECT_TITLE_EXPR + " AS object_title" +
                        " FROM search_alias sa" + OBJECT_TITLE_JOIN +
                        " WHERE sa.object_kind = :kind AND sa.object_id = :objectId" +
                        " ORDER BY sa.weight DESC, sa.updated_at DESC")
                .bind("kind", kind.name())
                .bind("objectId", objectId)
                .map(R2dbcSearchAliasRepository::mapView)
                .all();
    }

    @Override
    public Flux<SearchAliasView> searchByText(String query, int page, int size) {
        return databaseClient.sql("SELECT " + ALIAS_COLUMNS + ", " + OBJECT_TITLE_EXPR + " AS object_title" +
                        " FROM search_alias sa" + OBJECT_TITLE_JOIN +
                        " WHERE sa.alias_raw ILIKE :pattern OR sa.alias_norm ILIKE :pattern" +
                        " ORDER BY sa.alias_norm, sa.id" +
                        " LIMIT :limit OFFSET :offset")
                .bind("pattern", "%" + query + "%")
                .bind("limit", size)
                .bind("offset", (long) (page - 1) * size)
                .map(R2dbcSearchAliasRepository::mapView)
                .all();
    }

    @Override
    public Mono<Long> countByText(String query) {
        return databaseClient.sql("SELECT count(*) AS cnt FROM search_alias sa" +
                        " WHERE sa.alias_raw ILIKE :pattern OR sa.alias_norm ILIKE :pattern")
                .bind("pattern", "%" + query + "%")
                .map(row -> row.get("cnt", Long.class))
                .one();
    }

    @Override
    public Mono<Boolean> existsByObjectAndRaw(CatalogObjectKind kind, String objectId, String aliasRaw) {
        return databaseClient.sql("SELECT EXISTS(SELECT 1 FROM search_alias sa" +
                        " WHERE sa.object_kind = :kind AND sa.object_id = :objectId AND sa.alias_raw = :aliasRaw) AS present")
                .bind("kind", kind.name())
                .bind("objectId", objectId)
                .bind("aliasRaw", aliasRaw)
                .map(row -> Boolean.TRUE.equals(row.get("present", Boolean.class)))
                .one();
    }

    @Override
    public Flux<AliasCollision> findCollisions(String aliasNorm, CatalogObjectKind excludeKind, String excludeObjectId) {
        return databaseClient.sql("SELECT sa.id, sa.object_kind, sa.object_id, " + OBJECT_TITLE_EXPR + " AS object_title" +
                        " FROM search_alias sa" + OBJECT_TITLE_JOIN +
                        " WHERE sa.alias_norm = :aliasNorm" +
                        " AND NOT (sa.object_kind = :excludeKind AND sa.object_id = :excludeObjectId)" +
                        " ORDER BY sa.id")
                .bind("aliasNorm", aliasNorm)
                .bind("excludeKind", excludeKind.name())
                .bind("excludeObjectId", excludeObjectId)
                .map(row -> new AliasCollision(
                        row.get("id", Long.class),
                        CatalogObjectKind.fromString(row.get("object_kind", String.class)),
                        row.get("object_id", String.class),
                        row.get("object_title", String.class)))
                .all();
    }

    @Override
    public Mono<String> normalize(String raw) {
        return databaseClient.sql("SELECT search_norm(:raw) AS norm")
                .bind("raw", raw == null ? "" : raw)
                .map(row -> {
                    String norm = row.get("norm", String.class);
                    return norm == null ? "" : norm;
                })
                .one();
    }

    private static SearchAlias mapAlias(Readable row) {
        return SearchAlias.builder()
                .id(row.get("id", Long.class))
                .objectKind(CatalogObjectKind.fromString(row.get("object_kind", String.class)))
                .objectId(row.get("object_id", String.class))
                .aliasRaw(row.get("alias_raw", String.class))
                .aliasNorm(row.get("alias_norm", String.class))
                .weight(row.get("weight", BigDecimal.class))
                .source(AliasSource.fromString(row.get("source", String.class)))
                .createdBy(row.get("created_by", String.class))
                .createdAt(toInstant(row.get("created_at", OffsetDateTime.class)))
                .updatedAt(toInstant(row.get("updated_at", OffsetDateTime.class)))
                .build();
    }

    private static SearchAliasView mapView(Readable row) {
        return new SearchAliasView(mapAlias(row), row.get("object_title", String.class));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
