package biz.ugur.busroutebackend.admin.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Repository
@Slf4j
public class R2dbcCityRepository extends CityBaseRepository implements CityRepository {

    public R2dbcCityRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Flux<City> findActiveCities() {
        String sql = String.format(
                "SELECT %s FROM cities WHERE is_active = true ORDER BY display_order, name",
                selectColumns()
        );

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all()
                .doOnComplete(() -> log.debug("Found active cities"))
                .doOnError(error -> log.error("Failed to fetch active cities", error));
    }

    @Override
    public Mono<Boolean> existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM cities WHERE LOWER(name) = LOWER(:name)";

        return databaseClient.sql(sql)
                .bind("name", name)
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0)
                .doOnSuccess(exists -> log.debug("City exists check for name '{}': {}", name, exists));
    }

    @Override
    public Mono<Long> countActiveCities() {
        String sql = "SELECT COUNT(*) FROM cities WHERE is_active = true";

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one()
                .doOnSuccess(count -> log.debug("Active cities count: {}", count));
    }

    @Override
    public Mono<Boolean> existsByNameAndIdNot(String name, CityId id) {
        String sql = """
            SELECT COUNT(*) > 0 as exists
            FROM cities
            WHERE LOWER(name) = LOWER(:name) AND id != :id
            """;

        return databaseClient.sql(sql)
                .bind("name", name)
                .bind("id", id.getValue())
                .map(row -> row.get("exists", Boolean.class))
                .one()
                .doOnSuccess(exists -> log.debug("City exists check for name '{}' excluding ID {}: {}",
                        name, id.getValue(), exists));
    }
}
