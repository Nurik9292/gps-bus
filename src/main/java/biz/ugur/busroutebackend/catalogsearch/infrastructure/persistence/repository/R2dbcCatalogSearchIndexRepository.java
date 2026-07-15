package biz.ugur.busroutebackend.catalogsearch.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.RebuildStats;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchHit;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.catalogsearch.infrastructure.config.CatalogSearchProperties;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Locale;

@Repository
public class R2dbcCatalogSearchIndexRepository implements CatalogSearchIndexRepository {

    private static final String REBUILD_SQL =
            "SELECT inserted, orphan_aliases FROM catalog_search_rebuild(:kind, :objectId)";

    private static final String SEARCH_SQL = """
            WITH scored AS (
                SELECT si.object_kind, si.object_id, si.title, si.subtitle, si.source,
                       (word_similarity(:qn, si.term_norm) * si.weight
                        + CASE WHEN si.term_norm LIKE :qn || '%' THEN 0.3 ELSE 0 END
                        + CASE WHEN si.term_norm = :qn THEN 1.0 ELSE 0 END) AS score
                FROM search_index si
                WHERE :qn <% si.term_norm OR si.term_norm LIKE :qn || '%'
            ),
            best AS (
                SELECT DISTINCT ON (object_kind, object_id)
                       object_kind, object_id, title, subtitle, source, score
                FROM scored
                ORDER BY object_kind, object_id, score DESC
            )
            SELECT b.object_kind, b.object_id, b.title, b.subtitle, b.source, b.score,
                   bs.latitude AS stop_lat, bs.longitude AS stop_lon
            FROM best b
            LEFT JOIN bus_stops bs ON b.object_kind = 'STOP' AND bs.id = b.object_id
            ORDER BY b.score DESC, b.title
            LIMIT :limit
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final CatalogSearchProperties properties;

    public R2dbcCatalogSearchIndexRepository(DatabaseClient databaseClient,
                                             ReactiveTransactionManager transactionManager,
                                             CatalogSearchProperties properties) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
        this.properties = properties;
    }

    @Override
    public Mono<RebuildStats> rebuildAll() {
        return rebuild(null, null);
    }

    @Override
    public Mono<RebuildStats> rebuildObject(CatalogObjectKind kind, String objectId) {
        return rebuild(kind.name(), objectId);
    }

    private Mono<RebuildStats> rebuild(String kind, String objectId) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(REBUILD_SQL);
        spec = kind == null ? spec.bindNull("kind", String.class) : spec.bind("kind", kind);
        spec = objectId == null ? spec.bindNull("objectId", String.class) : spec.bind("objectId", objectId);
        return spec.map(row -> new RebuildStats(
                        longOf(row, "inserted"), longOf(row, "orphan_aliases")))
                .one()
                .as(transactionalOperator::transactional);
    }

    @Override
    public Flux<SearchHit> search(String normalizedQuery, int limit) {
        String threshold = String.format(Locale.ROOT, "%.4f", properties.getWordSimilarityThreshold());
        return databaseClient
                .sql("SET LOCAL pg_trgm.word_similarity_threshold = " + threshold)
                .fetch()
                .rowsUpdated()
                .thenMany(databaseClient.sql(SEARCH_SQL)
                        .bind("qn", normalizedQuery)
                        .bind("limit", limit)
                        .map(R2dbcCatalogSearchIndexRepository::mapHit)
                        .all())
                .as(transactionalOperator::transactional);
    }

    private static SearchHit mapHit(Readable row) {
        BigDecimal score = row.get("score", BigDecimal.class);
        return new SearchHit(
                CatalogObjectKind.fromString(row.get("object_kind", String.class)),
                row.get("object_id", String.class),
                row.get("title", String.class),
                row.get("subtitle", String.class),
                row.get("stop_lat", Double.class),
                row.get("stop_lon", Double.class),
                score == null ? 0.0 : score.doubleValue(),
                row.get("source", String.class));
    }

    private static long longOf(Readable row, String column) {
        Long value = row.get(column, Long.class);
        return value == null ? 0L : value;
    }
}
