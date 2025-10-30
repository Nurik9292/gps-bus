package biz.ugur.busroutebackend.client.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.client.domain.model.StopFavorite;
import biz.ugur.busroutebackend.client.domain.repository.StopFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@Slf4j
public class R2dbcStopFavoriteRepository extends StopFavoriteBaseRepository implements StopFavoriteRepository {

    public R2dbcStopFavoriteRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Flux<StopFavorite> findByClientId(ClientId clientId) {
        String sql = """
            SELECT * FROM stop_favorites
            WHERE client_id = :clientId
            ORDER BY created_at DESC
            """;

        return databaseClient.sql(sql)
                .bind("clientId", clientId.getValue())
                .map(getRowMapper())
                .all()
                .doOnComplete(() -> log.debug("Found stop favorites for client: {}", clientId.getValue()));
    }

    @Override
    public Mono<Boolean> existsByClientIdAndStopId(ClientId clientId, BusStopId stopId) {
        String sql = """
            SELECT COUNT(*) 
            FROM stop_favorites 
            WHERE client_id = :clientId AND stop_id = :stopId
            """;

        return databaseClient.sql(sql)
                .bind("clientId", clientId.getValue())
                .bind("stopId", stopId.getValue())
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0)
                .doOnNext(exists -> log.debug("Stop favorite exists check: clientId={}, stopId={}, exists={}",
                        clientId.getValue(), stopId.getValue(), exists));
    }

    @Override
    public Mono<Void> deleteByClientIdAndStopId(ClientId clientId, BusStopId stopId) {
        String sql = """
            DELETE FROM stop_favorites 
            WHERE client_id = :clientId AND stop_id = :stopId
            """;

        return databaseClient.sql(sql)
                .bind("clientId", clientId.getValue())
                .bind("stopId", stopId.getValue())
                .then()
                .doOnSuccess(v -> log.info("Deleted stop favorite: clientId={}, stopId={}",
                        clientId.getValue(), stopId.getValue()))
                .doOnError(error -> log.error("Failed to delete stop favorite: clientId={}, stopId={}, error={}",
                        clientId.getValue(), stopId.getValue(), error.getMessage()));
    }
}
