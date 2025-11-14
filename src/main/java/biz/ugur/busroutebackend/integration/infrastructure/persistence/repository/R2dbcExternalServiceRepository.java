package biz.ugur.busroutebackend.integration.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ApiToken;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Repository
@Slf4j
public class R2dbcExternalServiceRepository extends ExternalServiceBaseRepository implements ExternalServiceRepository {

    public R2dbcExternalServiceRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Mono<ExternalService> findByApiToken(ApiToken apiToken) {
        String sql = """
            SELECT * FROM external_services
            WHERE api_token = :apiToken
            """;

        return databaseClient.sql(sql)
                .bind("apiToken", apiToken.getValue())
                .map(getRowMapper())
                .one()
                .doOnNext(service -> log.debug("Found external service by token: {}", service.getName()))
                .doOnError(error -> log.error("Error finding external service by token", error));
    }

    @Override
    public Flux<ExternalService> findAllActive() {
        String sql = """
            SELECT * FROM external_services
            WHERE is_active = true
            ORDER BY name ASC
            """;

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all()
                .doOnComplete(() -> log.debug("Retrieved all active external services"));
    }

    @Override
    public Mono<Boolean> existsByName(String name) {
        String sql = """
            SELECT EXISTS(
                SELECT 1 FROM external_services
                WHERE name = :name
            ) AS exists_flag
            """;

        return databaseClient.sql(sql)
                .bind("name", name)
                .map(row -> row.get("exists_flag", Boolean.class))
                .one()
                .doOnNext(exists -> log.debug("External service with name '{}' exists: {}", name, exists));
    }

    @Override
    public Mono<Long> count() {
        String sql = "SELECT COUNT(*) AS cnt FROM external_services";

        return databaseClient.sql(sql)
                .map(row -> row.get("cnt", Long.class))
                .one();
    }

    @Override
    public Mono<Long> countActive() {
        String sql = "SELECT COUNT(*) AS cnt FROM external_services WHERE is_active = true";

        return databaseClient.sql(sql)
                .map(row -> row.get("cnt", Long.class))
                .one();
    }
}
