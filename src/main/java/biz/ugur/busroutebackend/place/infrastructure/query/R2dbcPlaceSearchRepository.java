package biz.ugur.busroutebackend.place.infrastructure.query;

import biz.ugur.busroutebackend.place.application.dto.PlaceAutocompleteResult;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchResult;
import biz.ugur.busroutebackend.place.domain.repository.PlaceSearchRepository;
import io.r2dbc.spi.Row;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Repository
public class R2dbcPlaceSearchRepository implements PlaceSearchRepository {

    private static final String SEARCH_SQL = """
            WITH scored AS (
                SELECT si.object_id, MAX(word_similarity(search_norm(:query), si.term_norm))::double precision AS sim
                FROM search_index si
                WHERE si.object_kind = 'PLACE'
                  AND (search_norm(:query) <% si.term_norm OR si.term_norm LIKE search_norm(:query) || '%')
                GROUP BY si.object_id
            )
            SELECT p.id, p.name, p.name_en, p.name_tm, p.description, p.address,
                   p.category, p.latitude, p.longitude, p.city_id,
                   COALESCE(array_agg(pa.alias) FILTER (WHERE pa.alias IS NOT NULL), '{}') AS aliases,
                   s.sim AS score
            FROM scored s
            JOIN places p ON p.id = s.object_id AND p.is_active = true
            LEFT JOIN place_aliases pa ON pa.place_id = p.id
            WHERE (:cityId::VARCHAR IS NULL OR p.city_id = :cityId)
              AND (:category::VARCHAR IS NULL OR p.category = :category)
            GROUP BY p.id, p.name, p.name_en, p.name_tm, p.description, p.address,
                     p.category, p.latitude, p.longitude, p.city_id, s.sim
            ORDER BY s.sim DESC, p.name ASC
            LIMIT :limit
            """;

    private static final String AUTOCOMPLETE_SQL = """
            SELECT p.id, p.name, p.category, p.address
            FROM (
                SELECT DISTINCT si.object_id
                FROM search_index si
                WHERE si.object_kind = 'PLACE'
                  AND si.term_norm LIKE search_norm(:query) || '%'
            ) s
            JOIN places p ON p.id = s.object_id AND p.is_active = true
            WHERE (:cityId::VARCHAR IS NULL OR p.city_id = :cityId)
            ORDER BY p.name ASC
            LIMIT :limit
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;

    public R2dbcPlaceSearchRepository(DatabaseClient databaseClient,
                                      ReactiveTransactionManager transactionManager) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    @Override
    public Flux<PlaceSearchResult> search(String query, String cityId, String category, int limit, double threshold) {
        String thresholdLiteral = String.format(Locale.ROOT, "%.4f", threshold);
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(SEARCH_SQL)
                .bind("query", query)
                .bind("limit", limit);
        spec = bindNullable(spec, "cityId", cityId);
        spec = bindNullable(spec, "category", category);
        DatabaseClient.GenericExecuteSpec boundSpec = spec;
        return databaseClient
                .sql("SET LOCAL pg_trgm.word_similarity_threshold = " + thresholdLiteral)
                .fetch()
                .rowsUpdated()
                .thenMany(boundSpec.map((row, metadata) -> mapToSearchResult(row, false)).all())
                .as(transactionalOperator::transactional);
    }

    @Override
    public Flux<PlaceSearchResult> searchNearby(double lat, double lng, int radiusMeters, String category, int limit) {
        PlaceSearchQueryBuilder.SearchQuery searchQuery =
                PlaceSearchQueryBuilder.buildNearbyQuery(lat, lng, radiusMeters, category, limit);

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(searchQuery.sql());
        for (Map.Entry<String, Object> entry : searchQuery.params().entrySet()) {
            spec = bindParam(spec, entry.getKey(), entry.getValue());
        }

        return spec.map((row, metadata) -> mapToSearchResult(row, true)).all();
    }

    @Override
    public Flux<PlaceAutocompleteResult> autocomplete(String query, String cityId, int limit) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(AUTOCOMPLETE_SQL)
                .bind("query", query)
                .bind("limit", limit);
        spec = bindNullable(spec, "cityId", cityId);

        return spec.map((row, metadata) -> new PlaceAutocompleteResult(
                row.get("id", String.class),
                row.get("name", String.class),
                row.get("category", String.class),
                row.get("address", String.class)
        )).all();
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                                  String name, String value) {
        return value == null || value.isBlank()
                ? spec.bindNull(name, String.class)
                : spec.bind(name, value);
    }

    private PlaceSearchResult mapToSearchResult(Row row, boolean isNearby) {
        List<String> aliases = parseAliases(row);
        Double score = isNearby ? row.get("distance", Double.class) : row.get("score", Double.class);

        return new PlaceSearchResult(
                row.get("id", String.class),
                row.get("name", String.class),
                row.get("name_en", String.class),
                row.get("name_tm", String.class),
                row.get("description", String.class),
                row.get("address", String.class),
                row.get("category", String.class),
                row.get("latitude", BigDecimal.class),
                row.get("longitude", BigDecimal.class),
                row.get("city_id", String.class),
                aliases,
                score
        );
    }

    private List<String> parseAliases(Row row) {
        try {
            String[] arr = row.get("aliases", String[].class);
            if (arr != null && arr.length > 0) {
                return Arrays.asList(arr);
            }
        } catch (Exception e) {
            log.trace("Could not parse aliases array", e);
        }
        return Collections.emptyList();
    }

    private DatabaseClient.GenericExecuteSpec bindParam(DatabaseClient.GenericExecuteSpec spec, String key, Object value) {
        if (value == null) {
            return spec.bindNull(key, Object.class);
        }
        if (value instanceof Number || value instanceof String || value instanceof Boolean) {
            return spec.bind(key, value);
        }
        return spec.bind(key, value.toString());
    }
}
