package biz.ugur.busroutebackend.client.infrastructure.repository;

import biz.ugur.busroutebackend.client.domain.model.StopFavorite;
import biz.ugur.busroutebackend.client.domain.repository.StopFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.StopFavoriteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
@Slf4j
public class R2dbcStopFavoriteRepository implements StopFavoriteRepository {

    private final DatabaseClient databaseClient;

    @Override
    public Mono<StopFavorite> save(StopFavorite stopFavorite) {
        return findById(stopFavorite.getId())
                .flatMap(existing -> update(stopFavorite))
                .switchIfEmpty(insert(stopFavorite))
                .doOnSuccess(saved -> log.debug("StopFavorite saved: clientId={}, stopId={}",
                        saved.getClientId(), saved.getStopId()))
                .doOnError(error -> log.error("Failed to save StopFavorite: clientId={}, stopId={}, error={}",
                        stopFavorite.getClientId(), stopFavorite.getStopId(), error.getMessage()));
    }

    @Override
    public Flux<StopFavorite> findByClientId(ClientId clientId) {
        String sql = """
            SELECT sf.id, sf.client_id, sf.stop_id, sf.created_at, sf.updated_at, sf.version
            FROM stop_favorites sf
            WHERE sf.client_id = :clientId
            ORDER BY sf.created_at DESC
            """;

        return databaseClient.sql(sql)
                .bind("clientId", clientId.getValue())
                .map(this::mapRowToStopFavorite)
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

    @Override
    public Mono<Void> deleteById(StopFavoriteId id) {
        String sql = "DELETE FROM stop_favorites WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", id.getValue())
                .then()
                .doOnSuccess(v -> log.info("Deleted stop favorite by id: {}", id.getValue()))
                .doOnError(error -> log.error("Failed to delete stop favorite by id: {}, error={}",
                        id.getValue(), error.getMessage()));
    }


    private Mono<StopFavorite> findById(StopFavoriteId id) {
        String sql = "SELECT * FROM stop_favorites WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", id.getValue())
                .map(this::mapRowToStopFavorite)
                .one()
                .onErrorReturn(new StopFavorite());
    }

    private Mono<StopFavorite> insert(StopFavorite stopFavorite) {
        String sql = """
            INSERT INTO stop_favorites (id, client_id, stop_id, created_at, updated_at, version)
            VALUES (:id, :clientId, :stopId, :createdAt, :updatedAt, :version)
            """;

        Instant now = Instant.now();

        return databaseClient.sql(sql)
                .bind("id", stopFavorite.getId().getValue())
                .bind("clientId", stopFavorite.getClientId())
                .bind("stopId", stopFavorite.getStopId())
                .bind("createdAt", now)
                .bind("updatedAt", now)
                .bind("version", 0L)
                .then()
                .thenReturn(stopFavorite)
                .doOnSuccess(saved -> log.info("New stop favorite created: clientId={}, stopId={}",
                        saved.getClientId(), saved.getStopId()));
    }

    private Mono<StopFavorite> update(StopFavorite stopFavorite) {
        String sql = """
            UPDATE stop_favorites 
            SET client_id = :clientId, stop_id = :stopId, updated_at = :updatedAt, version = version + 1
            WHERE id = :id
            """;

        return databaseClient.sql(sql)
                .bind("id", stopFavorite.getId().getValue())
                .bind("clientId", stopFavorite.getClientId())
                .bind("stopId", stopFavorite.getStopId())
                .bind("updatedAt", Instant.now())
                .then()
                .thenReturn(stopFavorite)
                .doOnSuccess(updated -> log.info("Stop favorite updated: clientId={}, stopId={}",
                        updated.getClientId(), updated.getStopId()));
    }

    private StopFavorite mapRowToStopFavorite(Row row, RowMetadata metadata) {
        StopFavorite stopFavorite = new StopFavorite(
                ClientId.of(row.get("client_id", String.class)),
                BusStopId.of(row.get("stop_id", String.class))
        );

        setField(stopFavorite, "id", StopFavoriteId.of(row.get("id", String.class)));
        setField(stopFavorite, "createdAt", row.get("created_at", Instant.class));
        setField(stopFavorite, "updatedAt", row.get("updated_at", Instant.class));
        setField(stopFavorite, "version", row.get("version", Long.class));

        return stopFavorite;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getSuperclass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException e) {
            try {
                Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
            } catch (Exception ex) {
                log.warn("Failed to set field {}: {}", fieldName, ex.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to set field {}: {}", fieldName, e.getMessage());
        }
    }
}
