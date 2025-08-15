package biz.ugur.busroutebackend.client.infrastructure.repository;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.repository.RouteFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.RouteFavoriteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
@Slf4j
public class R2dbcRouteFavoriteRepository implements RouteFavoriteRepository {

    private final DatabaseClient databaseClient;

    @Override
    public Mono<RouteFavorite> save(RouteFavorite routeFavorite) {
        return findById(routeFavorite.getId())
                .flatMap(existing -> update(routeFavorite))
                .switchIfEmpty(insert(routeFavorite))
                .doOnSuccess(saved -> log.debug("RouteFavorite saved: clientId={}, routeId={}",
                        saved.getClientId(), saved.getRouteId()))
                .doOnError(error -> log.error("Failed to save RouteFavorite: clientId={}, routeId={}, error={}",
                        routeFavorite.getClientId(), routeFavorite.getRouteId(), error.getMessage()));
    }

    @Override
    public Flux<RouteFavorite> findByClientId(ClientId clientId) {
        String sql = """
            SELECT rf.id, rf.client_id, rf.route_id, rf.created_at, rf.updated_at, rf.version
            FROM route_favorites rf
            WHERE rf.client_id = :clientId
            ORDER BY rf.created_at DESC
            """;

        return databaseClient.sql(sql)
                .bind("clientId", clientId.getValue())
                .map(this::mapRowToRouteFavorite)
                .all()
                .doOnComplete(() -> log.debug("Found route favorites for client: {}", clientId.getValue()));
    }

    @Override
    public Mono<Boolean> existsByClientIdAndRouteId(ClientId clientId, BusRouteId routeId) {
        String sql = """
            SELECT COUNT(*) 
            FROM route_favorites 
            WHERE client_id = :clientId AND route_id = :routeId
            """;

        return databaseClient.sql(sql)
                .bind("clientId", clientId.getValue())
                .bind("routeId", routeId.getValue())
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0)
                .doOnNext(exists -> log.debug("Route favorite exists check: clientId={}, routeId={}, exists={}",
                        clientId.getValue(), routeId.getValue(), exists));
    }

    @Override
    public Mono<Void> deleteByClientIdAndRouteId(ClientId clientId, BusRouteId routeId) {
        String sql = """
            DELETE FROM route_favorites 
            WHERE client_id = :clientId AND route_id = :routeId
            """;

        return databaseClient.sql(sql)
                .bind("clientId", clientId.getValue())
                .bind("routeId", routeId.getValue())
                .then()
                .doOnSuccess(v -> log.info("Deleted route favorite: clientId={}, routeId={}",
                        clientId.getValue(), routeId.getValue()))
                .doOnError(error -> log.error("Failed to delete route favorite: clientId={}, routeId={}, error={}",
                        clientId.getValue(), routeId.getValue(), error.getMessage()));
    }

    @Override
    public Mono<Void> deleteById(RouteFavoriteId id) {
        String sql = "DELETE FROM route_favorites WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", id.getValue())
                .then()
                .doOnSuccess(v -> log.info("Deleted route favorite by id: {}", id.getValue()))
                .doOnError(error -> log.error("Failed to delete route favorite by id: {}, error={}",
                        id.getValue(), error.getMessage()));
    }


    private Mono<RouteFavorite> findById(RouteFavoriteId id) {
        String sql = "SELECT * FROM route_favorites WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", id.getValue())
                .map(this::mapRowToRouteFavorite)
                .one()
                .onErrorReturn(new RouteFavorite());
    }

    private Mono<RouteFavorite> insert(RouteFavorite routeFavorite) {
        String sql = """
            INSERT INTO route_favorites (id, client_id, route_id, created_at, updated_at, version)
            VALUES (:id, :clientId, :routeId, :createdAt, :updatedAt, :version)
            """;

        Instant now = Instant.now();

        return databaseClient.sql(sql)
                .bind("id", routeFavorite.getId().getValue())
                .bind("clientId", routeFavorite.getClientId())
                .bind("routeId", routeFavorite.getRouteId())
                .bind("createdAt", now)
                .bind("updatedAt", now)
                .bind("version", 0L)
                .then()
                .thenReturn(routeFavorite)
                .doOnSuccess(saved -> log.info("New route favorite created: clientId={}, routeId={}",
                        saved.getClientId(), saved.getRouteId()));
    }

    private Mono<RouteFavorite> update(RouteFavorite routeFavorite) {
        String sql = """
            UPDATE route_favorites 
            SET client_id = :clientId, route_id = :routeId, updated_at = :updatedAt, version = version + 1
            WHERE id = :id
            """;

        return databaseClient.sql(sql)
                .bind("id", routeFavorite.getId().getValue())
                .bind("clientId", routeFavorite.getClientId())
                .bind("routeId", routeFavorite.getRouteId())
                .bind("updatedAt", Instant.now())
                .then()
                .thenReturn(routeFavorite)
                .doOnSuccess(updated -> log.info("Route favorite updated: clientId={}, routeId={}",
                        updated.getClientId(), updated.getRouteId()));
    }

    private RouteFavorite mapRowToRouteFavorite(Row row, RowMetadata metadata) {
        RouteFavorite routeFavorite = new RouteFavorite(
                ClientId.of(row.get("client_id", String.class)),
                BusRouteId.of(row.get("route_id", String.class))
        );

        setField(routeFavorite, "id", RouteFavoriteId.of(row.get("id", String.class)));
        setField(routeFavorite, "createdAt", row.get("created_at", Instant.class));
        setField(routeFavorite, "updatedAt", row.get("updated_at", Instant.class));
        setField(routeFavorite, "version", row.get("version", Long.class));

        return routeFavorite;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getSuperclass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException e) {
            try {
                java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
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