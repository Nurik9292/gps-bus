package biz.ugur.busroutebackend.place.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.place.domain.model.Place;
import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Repository
public class R2dbcPlaceRepository extends PlaceBaseRepository implements PlaceRepository {

    public R2dbcPlaceRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Flux<Place> findByCityId(String cityId) {
        String sql = String.format(
                "SELECT %s FROM places WHERE city_id = :cityId AND is_active = true ORDER BY name",
                selectColumns()
        );
        return databaseClient.sql(sql)
                .bind("cityId", cityId)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<Place> findByCityId(String cityId, Pageable pageable) {
        String sql = String.format(
                "SELECT %s FROM places WHERE city_id = :cityId %s LIMIT :limit OFFSET :offset",
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
    public Mono<Boolean> existsByNameAndCityId(String name, String cityId) {
        String sql = "SELECT EXISTS(SELECT 1 FROM places WHERE LOWER(name) = LOWER(:name) AND city_id = :cityId)";
        return databaseClient.sql(sql)
                .bind("name", name)
                .bind("cityId", cityId)
                .map(row -> row.get(0, Boolean.class))
                .one();
    }

    @Override
    public Flux<Place> findByCategory(String category, Pageable pageable) {
        String sql = String.format(
                "SELECT %s FROM places WHERE category = :category AND is_active = true %s LIMIT :limit OFFSET :offset",
                selectColumns(),
                getOrderByClause(pageable)
        );
        return databaseClient.sql(sql)
                .bind("category", category)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Long> countByCityId(String cityId) {
        String sql = "SELECT COUNT(*) FROM places WHERE city_id = :cityId";
        return databaseClient.sql(sql)
                .bind("cityId", cityId)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Mono<Long> countByFilters(String cityId, String category, String search) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM places WHERE 1=1");
        List<String> conditions = new ArrayList<>();

        if (cityId != null) conditions.add(" AND city_id = :cityId");
        if (category != null) conditions.add(" AND category = :category");
        if (search != null && !search.isBlank()) conditions.add(" AND (name ILIKE :search OR name_en ILIKE :search OR name_tm ILIKE :search)");

        conditions.forEach(sql::append);

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());
        if (cityId != null) spec = spec.bind("cityId", cityId);
        if (category != null) spec = spec.bind("category", category);
        if (search != null && !search.isBlank()) spec = spec.bind("search", "%" + search + "%");

        return spec.map(row -> row.get(0, Long.class)).one();
    }

    @Override
    public Flux<Place> findByFilters(String cityId, String category, String search, Pageable pageable) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(selectColumns())
                .append(" FROM places WHERE 1=1");

        if (cityId != null) sql.append(" AND city_id = :cityId");
        if (category != null) sql.append(" AND category = :category");
        if (search != null && !search.isBlank()) sql.append(" AND (name ILIKE :search OR name_en ILIKE :search OR name_tm ILIKE :search)");

        sql.append(" ").append(getOrderByClause(pageable));
        sql.append(" LIMIT :limit OFFSET :offset");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());
        if (cityId != null) spec = spec.bind("cityId", cityId);
        if (category != null) spec = spec.bind("category", category);
        if (search != null && !search.isBlank()) spec = spec.bind("search", "%" + search + "%");
        spec = spec.bind("limit", pageable.getPageSize());
        spec = spec.bind("offset", pageable.getOffset());

        return spec.map(getRowMapper()).all();
    }
}
