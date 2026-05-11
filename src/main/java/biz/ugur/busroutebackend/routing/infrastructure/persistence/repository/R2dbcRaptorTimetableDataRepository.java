package biz.ugur.busroutebackend.routing.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.routing.domain.repository.RaptorTimetableDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalTime;
import java.util.List;

@Repository
@Slf4j
public class R2dbcRaptorTimetableDataRepository implements RaptorTimetableDataRepository {

    private static final String TRIPS_SQL = """
            SELECT id, route_id, direction, service_id,
                   headway_seconds, start_time, end_time
              FROM trips
             WHERE is_active = true
            """;

    private static final String STOP_TIMES_SQL = """
            SELECT st.trip_id, st.stop_sequence, st.stop_id,
                   st.arrival_offset_sec, st.departure_offset_sec
              FROM stop_times st
              JOIN trips t ON t.id = st.trip_id
             WHERE t.is_active = true
             ORDER BY st.trip_id, st.stop_sequence
            """;

    private static final String TRANSFERS_SQL = """
            SELECT from_stop_id, to_stop_id, walking_seconds, distance_meters
              FROM stop_transfers
            """;

    private final DatabaseClient databaseClient;

    public R2dbcRaptorTimetableDataRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<RawTimetableData> loadAll() {
        Mono<List<TripRow>> tripsMono = databaseClient.sql(TRIPS_SQL)
                .map((row, meta) -> new TripRow(
                        row.get("id", String.class),
                        row.get("route_id", String.class),
                        row.get("direction", Short.class).intValue(),
                        row.get("service_id", String.class),
                        row.get("headway_seconds", Integer.class),
                        row.get("start_time", LocalTime.class),
                        row.get("end_time", LocalTime.class)))
                .all()
                .collectList();

        Mono<List<StopTimeRow>> stopTimesMono = databaseClient.sql(STOP_TIMES_SQL)
                .map((row, meta) -> new StopTimeRow(
                        row.get("trip_id", String.class),
                        row.get("stop_sequence", Integer.class),
                        row.get("stop_id", String.class),
                        row.get("arrival_offset_sec", Integer.class),
                        row.get("departure_offset_sec", Integer.class)))
                .all()
                .collectList();

        Mono<List<TransferRow>> transfersMono = databaseClient.sql(TRANSFERS_SQL)
                .map((row, meta) -> new TransferRow(
                        row.get("from_stop_id", String.class),
                        row.get("to_stop_id", String.class),
                        row.get("walking_seconds", Integer.class),
                        row.get("distance_meters", Integer.class)))
                .all()
                .collectList();

        return Mono.zip(tripsMono, stopTimesMono, transfersMono)
                .map(t -> new RawTimetableData(t.getT1(), t.getT2(), t.getT3()))
                .doOnNext(d -> log.info("[RAPTOR_CACHE] loaded raw data: trips={} stop_times={} transfers={}",
                        d.trips().size(), d.stopTimes().size(), d.transfers().size()));
    }
}
