package biz.ugur.busroutebackend.routing.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.routing.domain.model.raptor.StopTransfer;
import biz.ugur.busroutebackend.routing.domain.repository.StopTransferRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
@Slf4j
public class R2dbcStopTransferRepository implements StopTransferRepository {

    private static final String INSERT_SQL = """
            INSERT INTO stop_transfers (
                from_stop_id, to_stop_id, walking_seconds, distance_meters, transfer_type
            ) VALUES (
                :from_stop_id, :to_stop_id, :walking_seconds, :distance_meters, :transfer_type
            )
            """;

    private final DatabaseClient databaseClient;

    public R2dbcStopTransferRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<StopTransfer> saveAll(List<StopTransfer> transfers) {
        if (transfers == null || transfers.isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromIterable(transfers).concatMap(this::insertOne);
    }

    @Override
    public Mono<Long> count() {
        return databaseClient.sql("SELECT COUNT(*) AS c FROM stop_transfers")
                .map(row -> row.get("c", Long.class))
                .one();
    }

    @Override
    public Mono<Long> deleteAll() {
        return databaseClient.sql("DELETE FROM stop_transfers")
                .fetch()
                .rowsUpdated();
    }

    private Mono<StopTransfer> insertOne(StopTransfer transfer) {
        return databaseClient.sql(INSERT_SQL)
                .bind("from_stop_id", transfer.fromStopId().getValue())
                .bind("to_stop_id", transfer.toStopId().getValue())
                .bind("walking_seconds", transfer.walkingSeconds())
                .bind("distance_meters", transfer.distanceMeters())
                .bind("transfer_type", transfer.transferType().getValue())
                .fetch()
                .rowsUpdated()
                .thenReturn(transfer);
    }
}
