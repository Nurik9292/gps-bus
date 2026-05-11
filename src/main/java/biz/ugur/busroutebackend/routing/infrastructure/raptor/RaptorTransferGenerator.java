package biz.ugur.busroutebackend.routing.infrastructure.raptor;

import biz.ugur.busroutebackend.routing.domain.model.raptor.StopTransfer;
import biz.ugur.busroutebackend.routing.domain.repository.StopTransferRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class RaptorTransferGenerator {

    private final DatabaseClient databaseClient;
    private final StopTransferRepository stopTransferRepository;

    @Value("${routing.raptor.transfer-radius-meters:200}")
    private int transferRadiusMeters;

    @Value("${routing.raptor.transfer-walking-speed-kmh:5.0}")
    private double walkingSpeedKmh;

    public RaptorTransferGenerator(DatabaseClient databaseClient,
                                    StopTransferRepository stopTransferRepository) {
        this.databaseClient = databaseClient;
        this.stopTransferRepository = stopTransferRepository;
    }

    public Mono<GenerationResult> regenerate() {
        double metersPerSecond = walkingSpeedKmh * 1000.0 / 3600.0;
        if (metersPerSecond <= 0) {
            return Mono.error(new IllegalStateException(
                    "Invalid walking speed: " + walkingSpeedKmh + " km/h"));
        }

        return stopTransferRepository.deleteAll()
                .then(loadPairs())
                .flatMap(pairs -> {
                    List<StopTransfer> transfers = new ArrayList<>(pairs.size() * 2);
                    for (PairRow row : pairs) {
                        int walkingSec = Math.max(1, (int) Math.round(row.distanceMeters() / metersPerSecond));
                        int distanceInt = Math.max(1, (int) Math.round(row.distanceMeters()));
                        BusStopId a = BusStopId.of(row.aId());
                        BusStopId b = BusStopId.of(row.bId());
                        transfers.add(StopTransfer.footpath(a, b, walkingSec, distanceInt));
                        transfers.add(StopTransfer.footpath(b, a, walkingSec, distanceInt));
                    }
                    log.info("[RAPTOR_GEN] generating {} stop_transfers from {} pairs (radius={}m)",
                            transfers.size(), pairs.size(), transferRadiusMeters);
                    return stopTransferRepository.saveAll(transfers)
                            .then(Mono.just(new GenerationResult(transfers.size(), pairs.size())));
                });
    }

    private Mono<List<PairRow>> loadPairs() {
        String sql = """
                SELECT
                    a.id AS a_id,
                    b.id AS b_id,
                    ST_DistanceSphere(
                        ST_MakePoint(a.longitude, a.latitude),
                        ST_MakePoint(b.longitude, b.latitude)
                    ) AS distance_m
                FROM bus_stops a
                JOIN bus_stops b
                  ON b.id > a.id
                 AND a.is_active = true
                 AND b.is_active = true
                 AND ST_DWithin(
                       ST_MakePoint(a.longitude, a.latitude)::geography,
                       ST_MakePoint(b.longitude, b.latitude)::geography,
                       :radius)
                """;
        return databaseClient.sql(sql)
                .bind("radius", transferRadiusMeters)
                .map((row, meta) -> new PairRow(
                        row.get("a_id", String.class),
                        row.get("b_id", String.class),
                        row.get("distance_m", Double.class)))
                .all()
                .collectList();
    }

    public record GenerationResult(int rowsInserted, int pairsFound) {
    }

    private record PairRow(String aId, String bId, Double distanceMeters) {
    }
}
