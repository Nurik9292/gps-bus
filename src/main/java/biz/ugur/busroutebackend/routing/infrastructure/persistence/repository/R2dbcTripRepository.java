package biz.ugur.busroutebackend.routing.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.routing.domain.model.raptor.Trip;
import biz.ugur.busroutebackend.routing.domain.repository.TripRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@Slf4j
public class R2dbcTripRepository implements TripRepository {

    private static final String INSERT_SQL = """
            INSERT INTO trips (
                id, route_id, direction, service_id, headway_seconds,
                start_time, end_time, is_active,
                created_at, updated_at, version
            ) VALUES (
                :id, :route_id, :direction, :service_id, :headway_seconds,
                :start_time, :end_time, :is_active,
                :created_at, :updated_at, :version
            )
            """;

    private final DatabaseClient databaseClient;

    public R2dbcTripRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Trip> save(Trip trip) {
        LocalDateTime now = LocalDateTime.now();
        return databaseClient.sql(INSERT_SQL)
                .bind("id", trip.getId().getValue())
                .bind("route_id", trip.getRouteId().getValue())
                .bind("direction", trip.getDirection().getValue())
                .bind("service_id", trip.getServiceId())
                .bind("headway_seconds", trip.getHeadwaySeconds() != null ? trip.getHeadwaySeconds() : null)
                .bind("start_time", trip.getStartTime())
                .bind("end_time", trip.getEndTime())
                .bind("is_active", Boolean.TRUE.equals(trip.getIsActive()))
                .bind("created_at", now)
                .bind("updated_at", now)
                .bind("version", trip.getVersion() != null ? trip.getVersion() : 0L)
                .fetch()
                .rowsUpdated()
                .thenReturn(trip.toBuilder().createdAt(now).updatedAt(now).build());
    }

    @Override
    public Flux<Trip> saveAll(List<Trip> trips) {
        if (trips == null || trips.isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromIterable(trips).concatMap(this::save);
    }

    @Override
    public Mono<Long> count() {
        return databaseClient.sql("SELECT COUNT(*) AS c FROM trips")
                .map(row -> row.get("c", Long.class))
                .one();
    }

    @Override
    public Mono<Long> deleteAll() {
        return databaseClient.sql("DELETE FROM trips")
                .fetch()
                .rowsUpdated();
    }
}
