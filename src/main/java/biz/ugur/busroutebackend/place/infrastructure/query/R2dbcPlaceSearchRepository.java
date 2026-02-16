package biz.ugur.busroutebackend.place.infrastructure.query;

import biz.ugur.busroutebackend.place.application.dto.PlaceAutocompleteResult;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchResult;
import biz.ugur.busroutebackend.place.domain.repository.PlaceSearchRepository;
import io.r2dbc.spi.Row;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class R2dbcPlaceSearchRepository implements PlaceSearchRepository {

    private final DatabaseClient databaseClient;

    public R2dbcPlaceSearchRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<PlaceSearchResult> search(String query, String cityId, String category, int limit, double threshold) {
        PlaceSearchQueryBuilder.SearchQuery searchQuery =
                PlaceSearchQueryBuilder.buildSearchQuery(query, cityId, category, limit, threshold);

        return executeSearchQuery(searchQuery);
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
        PlaceSearchQueryBuilder.SearchQuery searchQuery =
                PlaceSearchQueryBuilder.buildAutocompleteQuery(query, cityId, limit);

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(searchQuery.sql());
        for (Map.Entry<String, Object> entry : searchQuery.params().entrySet()) {
            spec = bindParam(spec, entry.getKey(), entry.getValue());
        }

        return spec.map((row, metadata) -> new PlaceAutocompleteResult(
                row.get("id", String.class),
                row.get("name", String.class),
                row.get("category", String.class),
                row.get("address", String.class)
        )).all();
    }

    private Flux<PlaceSearchResult> executeSearchQuery(PlaceSearchQueryBuilder.SearchQuery searchQuery) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(searchQuery.sql());
        for (Map.Entry<String, Object> entry : searchQuery.params().entrySet()) {
            spec = bindParam(spec, entry.getKey(), entry.getValue());
        }

        return spec.map((row, metadata) -> mapToSearchResult(row, false)).all();
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
