package biz.ugur.busroutebackend.routing.infrastructure.repository;

import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.repository.TripPlanRepository;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripPlanId;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
@Slf4j
public class R2dbcTripPlanRepository implements TripPlanRepository {

    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    public R2dbcTripPlanRepository(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<TripPlan> save(TripPlan tripPlan) {

        String sql = """
            INSERT INTO trip_plans (
                                    id, 
                                    origin_latitude, 
                                    origin_longitude, 
                                    destination_latitude, 
                                    destination_longitude, 
                                    search_time, 
                                    options_count, 
                                    max_transfers, 
                                    max_walking_distance_meters,
                                    created_at)
            VALUES (
                    :id, 
                    :originLat, 
                    :originLon, 
                    :destLat, 
                    :destLon,
                    :searchTime, 
                    :optionsCount, 
                    :maxTransfers, 
                    :maxWalkingDistance, 
                    :createdAt)
            ON CONFLICT (id) DO UPDATE SET
                options_count = :optionsCount,
                updated_at = CURRENT_TIMESTAMP
            """;

        return databaseClient.sql(sql)
                .bind("id", tripPlan.getId().getValue())
                .bind("originLat", tripPlan.getOriginLatitude())
                .bind("originLon", tripPlan.getOriginLongitude())
                .bind("destLat", tripPlan.getDestinationLatitude())
                .bind("destLon", tripPlan.getDestinationLongitude())
                .bind("searchTime", tripPlan.getSearchTimeDb())
                .bind("optionsCount", tripPlan.getOptionsCount())
                .bind("maxTransfers", tripPlan.getMaxTransfers())
                .bind("maxWalkingDistance", tripPlan.getMaxWalkingDistanceMeters())
                .bind("createdAt", LocalDateTime.now())
                .then()
                .thenReturn(tripPlan)
                .doOnSuccess(plan -> log.debug("Saved trip plan: {}", plan.getId().getValue()))
                .doOnError(error -> log.error("Failed to save trip plan: {}", error.getMessage()));
    }

    @Override
    public Mono<TripPlan> findById(TripPlanId tripPlanId) {
        String sql = """
            SELECT id, origin_latitude, origin_longitude, destination_latitude, destination_longitude,
                   search_time, options_count, max_transfers, max_walking_distance_meters
            FROM trip_plans 
            WHERE id = :id
            """;

        return databaseClient.sql(sql)
                .bind("id", tripPlanId.getValue())
                .map((row, metadata) -> new TripPlan(
                        TripPlanId.of(row.get("id", String.class)),
                        row.get("origin_latitude", Double.class),
                        row.get("origin_longitude", Double.class),
                        row.get("destination_latitude", Double.class),
                        row.get("destination_longitude", Double.class),
                        row.get("search_time", LocalDateTime.class),
                        row.get("options_count", Integer.class),
                        row.get("max_transfers", Integer.class),
                        row.get("max_walking_distance_meters", Integer.class)
                ))
                .first();
    }

    @Override
    public Flux<TripPlan> findRecentPlans(int limit) {
        return Flux.empty(); // Simplified implementation
    }

    @Override
    public Flux<TripPlan> findPlansByTimeRange(LocalDateTime from, LocalDateTime to) {
        return Flux.empty(); // Simplified implementation
    }

    @Override
    public Mono<Void> deleteById(TripPlanId tripPlanId) {
        String sql = "DELETE FROM trip_plans WHERE id = :id";
        return databaseClient.sql(sql)
                .bind("id", tripPlanId.getValue())
                .then();
    }

    @Override
    public Mono<Long> countTotalPlans() {
        String sql = "SELECT COUNT(*) FROM trip_plans";
        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Flux<TripPlanningStatistics> getTripPlanningStatistics(LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT 
                DATE(created_at) as search_date,
                COUNT(*) as total_searches,
                COUNT(*) FILTER (WHERE options_count > 0) as successful_searches,
                AVG(options_count) as avg_options_found
            FROM trip_plans 
            WHERE created_at BETWEEN :from AND :to
            GROUP BY DATE(created_at)
            ORDER BY search_date DESC
            """;

        return databaseClient.sql(sql)
                .bind("from", from)
                .bind("to", to)
                .map(row -> new TripPlanningStatistics(
                        row.get("search_date", LocalDateTime.class),
                        row.get("total_searches", Long.class),
                        row.get("successful_searches", Long.class),
                        row.get("avg_options_found", Double.class),
                        0.0,
                        "Unknown" //
                ))
                .all();
    }
}