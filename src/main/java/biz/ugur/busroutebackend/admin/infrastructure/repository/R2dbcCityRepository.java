package biz.ugur.busroutebackend.admin.infrastructure.repository;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
@Slf4j
public class R2dbcCityRepository implements CityRepository {

    private final DatabaseClient databaseClient;

    public R2dbcCityRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<City> save(City city) {
        if (city.getId() == null) {
            return insert(city);
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

    private City mapRowToCity(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        return new City(
                CityId.of(row.get("id", String.class)),
                row.get("name", String.class),
                row.get("name_tm", String.class),
                row.get("is_active", Boolean.class),
                row.get("display_order", Integer.class)
        );
    }
}