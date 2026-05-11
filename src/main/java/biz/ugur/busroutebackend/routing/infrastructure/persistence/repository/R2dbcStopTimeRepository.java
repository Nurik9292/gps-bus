package biz.ugur.busroutebackend.routing.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.routing.domain.model.raptor.StopTime;
import biz.ugur.busroutebackend.routing.domain.repository.StopTimeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
@Slf4j
public class R2dbcStopTimeRepository implements StopTimeRepository {

    private static final String INSERT_SQL = """
            INSERT INTO stop_times (
                trip_id, stop_sequence, stop_id,
                arrival_offset_sec, departure_offset_sec
            ) VALUES (
                :trip_id, :stop_sequence, :stop_id,
                :arrival_offset_sec, :departure_offset_sec
            )
            """;

    private final DatabaseClient databaseClient;

    public R2dbcStopTimeRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<StopTime> saveAll(List<StopTime> stopTimes) {
        if (stopTimes == null || stopTimes.isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromIterable(stopTimes).concatMap(this::insertOne);
    }

    @Override
    public Mono<Long> count() {
        return databaseClient.sql("SELECT COUNT(*) AS c FROM stop_times")
                .map(row -> row.get("c", Long.class))
                .one();
    }

    @Override
    public Mono<Long> deleteAll() {
        return databaseClient.sql("DELETE FROM stop_times")
                .fetch()
                .rowsUpdated();
    }

    private Mono<StopTime> insertOne(StopTime stopTime) {
        return databaseClient.sql(INSERT_SQL)
                .bind("trip_id", stopTime.tripId().getValue())
                .bind("stop_sequence", stopTime.stopSequence())
                .bind("stop_id", stopTime.stopId().getValue())
                .bind("arrival_offset_sec", stopTime.arrivalOffsetSec())
                .bind("departure_offset_sec", stopTime.departureOffsetSec())
                .fetch()
                .rowsUpdated()
                .thenReturn(stopTime);
    }
}
