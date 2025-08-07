package biz.ugur.busroutebackend.admin.infrastructure.repository;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
@Slf4j
@Transactional(readOnly = true)
public class R2dbcCityRepository implements CityRepository {

    private final DatabaseClient databaseClient;

    public R2dbcCityRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    @Transactional
    public Mono<City> save(City city) {
        if (city.isNew()) {
            return insert(city).doOnSuccess(City::markAsExisting);
        } else {
            return update(city);
        }
    }

    private Mono<City> insert(City city) {
        String sql = """
            INSERT INTO cities (id, name, name_tm, is_active, display_order, created_at, updated_at, version)
            VALUES (:id, :name, :nameTm, :isActive, :displayOrder, :createdAt, :updatedAt, :version)
            """;

        Instant now = Instant.now();
        return databaseClient.sql(sql)
                .bind("id", city.getId().getValue())
                .bind("name", city.getName())
                .bind("nameTm", city.getNameTm())
                .bind("isActive", city.getIsActive())
                .bind("displayOrder", city.getDisplayOrder())
                .bind("createdAt", now)
                .bind("updatedAt", now)
                .bind("version", 0L)
                .then()
                .thenReturn(city);
    }

    private Mono<City> update(City city) {
        String sql = """
            UPDATE cities 
            SET name = :name, name_tm = :nameTm, is_active = :isActive, 
                display_order = :displayOrder, updated_at = :updatedAt, version = version + 1
            WHERE id = :id
            """;

        return databaseClient.sql(sql)
                .bind("id", city.getId().getValue())
                .bind("name", city.getName())
                .bind("nameTm", city.getNameTm())
                .bind("isActive", city.getIsActive())
                .bind("displayOrder", city.getDisplayOrder())
                .bind("updatedAt", Instant.now())
                .then()
                .thenReturn(city);
    }

    @Override
    public Flux<City> findAllPaged(Boolean isActive, Pageable pageable) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT id, name, name_tm, is_active, display_order, created_at, updated_at
            FROM cities
            """);

        if (isActive != null) {
            sqlBuilder.append("WHERE is_active = :isActive ");
        }

        sqlBuilder.append("ORDER BY ");
        if (pageable.getSort().isSorted()) {
            Sort.Order order = pageable.getSort().iterator().next();
            String sortField = mapSortField(order.getProperty());
            String direction = order.getDirection().name();
            sqlBuilder.append(sortField).append(" ").append(direction);
        } else {
            sqlBuilder.append("name ASC");
        }

        sqlBuilder.append(" LIMIT :limit OFFSET :offset");

        DatabaseClient.GenericExecuteSpec query = databaseClient.sql(sqlBuilder.toString())
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset());

        if (isActive != null) {
            query = query.bind("isActive", isActive);
        }

        return query.map(this::mapRowToCity)
                .all()
                .doOnComplete(() -> log.debug("Found cities with pagination: page={}, size={}",
                        pageable.getPageNumber() + 1, pageable.getPageSize()))
                .doOnError(error -> log.error("Failed to fetch paged cities", error));
    }

    @Override
    public Mono<City> findById(CityId cityId) {
        String sql = "SELECT * FROM cities WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", cityId.getValue())
                .map(this::mapRowToCity)
                .one();
    }

    @Override
    public Flux<City> findActiveCities() {
        String sql = "SELECT * FROM cities WHERE is_active = true ORDER BY display_order, name";

        return databaseClient.sql(sql)
                .map(this::mapRowToCity)
                .all();
    }

    @Override
    public Flux<City> findAllCities() {
        String sql = "SELECT * FROM cities ORDER BY display_order, name";

        return databaseClient.sql(sql)
                .map(this::mapRowToCity)
                .all();
    }

    @Override
    public Mono<Boolean> existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM cities WHERE LOWER(name) = LOWER(:name)";

        return databaseClient.sql(sql)
                .bind("name", name)
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0);
    }

    @Override
    @Transactional
    public Mono<Void> deleteById(CityId cityId) {
        String sql = "DELETE FROM cities WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", cityId.getValue())
                .then();
    }

    @Override
    public Mono<Long> countActiveCities() {
        String sql = "SELECT COUNT(*) FROM cities WHERE is_active = true";

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Mono<Boolean> existsByNameAndIdNot(String name, CityId id) {
        return databaseClient.sql("""
                SELECT COUNT(*) > 0 as exists 
                FROM cities 
                WHERE LOWER(name) = LOWER(:name) AND id != :id
                """)
                .bind("name", name)
                .bind("id", id.getValue())
                .map(row -> row.get("exists", Boolean.class))
                .one()
                .doOnSuccess(exists -> log.debug("City exists check for name '{}' excluding ID {}: {}", name, id.getValue(), exists));
    }



    private City mapRowToCity(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        return new City(
                CityId.of(row.get("id", String.class)),
                row.get("name", String.class),
                row.get("name_tm", String.class),
                row.get("is_active", Boolean.class),
                row.get("display_order", Integer.class)
        );
    }

    private String mapSortField(String field) {
        return switch (field) {
            case "nameTm" -> "name_tm";
            case "displayOrder" -> "display_order";
            case "createdAt" -> "created_at";
            case "updatedAt" -> "updated_at";
            default -> "name";
        };
    }
}