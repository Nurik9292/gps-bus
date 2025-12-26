package biz.ugur.busroutebackend.routing.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.routing.domain.repository.RoutingStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
@RequiredArgsConstructor
public class R2dbcRoutingStatisticsRepository implements RoutingStatisticsRepository {

    private final DatabaseClient databaseClient;

    @Override
    public Flux<String> getPopularRoutes(int limit) {
        return databaseClient.sql("""
                SELECT route_number
                FROM mv_active_routes_summary
                WHERE active_vehicles >= 1
                ORDER BY active_vehicles DESC, moving_vehicles DESC
                LIMIT :limit
                """)
                .bind("limit", limit)
                .map(row -> row.get("route_number", String.class))
                .all();
    }
}
