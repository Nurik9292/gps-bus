package biz.ugur.busroutebackend.client.infrastructure.repository;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.repository.RouteFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.RouteFavoriteId;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Repository
@Slf4j
public class R2dbcRouteFavoriteRepository extends BaseR2dbcRepository<RouteFavorite, RouteFavoriteId> implements RouteFavoriteRepository {

    public R2dbcRouteFavoriteRepository(DatabaseClient databaseClient) {
        super(databaseClient, "route_favorites", RouteFavorite.class);
    }

    @Override
    protected String convertIdToDatabase(RouteFavoriteId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, RouteFavorite> getRowMapper() {
        return this::mapRowToRouteFavorite;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(RouteFavorite routeFavorite) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", routeFavorite.getId().getValue());
        values.put("client_id", routeFavorite.getClientId());
        values.put("route_id", routeFavorite.getRouteId());
        return values;
    }

    @Override
    public Flux<RouteFavorite> findByClientId(ClientId clientId) {
        String sql = """
            SELECT * FROM route_favorites 
            WHERE client_id = :clientId 
            ORDER BY created_at DESC
            """;

        return databaseClient.sql(sql)
                .bind("clientId", clientId.getValue())
                .map(getRowMapper())
                .all()
                .doOnComplete(() -> log.debug("Found route favorites for client: {}", clientId.getValue()))
                .doOnError(error -> log.error("Failed to find route favorites for client: {}", clientId.getValue(), error));
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
                .doOnSuccess(exists -> log.debug("Route favorite exists check: clientId={}, routeId={}, exists={}",
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
                .fetch()
                .rowsUpdated()
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Deleted route favorite: clientId={}, routeId={}",
                                clientId.getValue(), routeId.getValue());
                    } else {
                        log.warn("No route favorite found to delete: clientId={}, routeId={}",
                                clientId.getValue(), routeId.getValue());
                    }
                })
                .doOnError(error -> log.error("Failed to delete route favorite: clientId={}, routeId={}",
                        clientId.getValue(), routeId.getValue(), error))
                .then();
    }

    private RouteFavorite mapRowToRouteFavorite(Row row, RowMetadata metadata) {
        return RouteFavorite.fromDatabase(
                RouteFavoriteId.of(row.get("id", String.class)),
                ClientId.of(row.get("client_id", String.class)),
                BusRouteId.of(row.get("route_id", String.class)),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class),
                row.get("version", Long.class)
        );
    }
}