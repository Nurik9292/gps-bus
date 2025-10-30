package biz.ugur.busroutebackend.client.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.repository.RouteFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@Slf4j
public class R2dbcRouteFavoriteRepository extends RouteFavoriteBaseRepository implements RouteFavoriteRepository {

    public R2dbcRouteFavoriteRepository(DatabaseClient databaseClient) {
        super(databaseClient);
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
}