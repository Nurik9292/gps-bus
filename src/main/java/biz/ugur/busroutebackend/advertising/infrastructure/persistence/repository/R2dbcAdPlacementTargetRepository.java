package biz.ugur.busroutebackend.advertising.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class R2dbcAdPlacementTargetRepository implements AdPlacementTargetRepository {

    private static final String SELECT_COLUMNS = "id, placement_id, target_type, target_id, created_at";

    private final DatabaseClient databaseClient;

    public R2dbcAdPlacementTargetRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<PlacementTarget> findByPlacementId(PlacementId placementId) {
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM ad_placement_targets WHERE placement_id = :placementId"
                + " ORDER BY created_at ASC";
        return databaseClient.sql(sql)
                .bind("placementId", placementId.getValue())
                .map((row, meta) -> PlacementTarget.of(
                        TargetType.from(row.get("target_type", String.class)),
                        row.get("target_id", String.class)))
                .all();
    }

    @Override
    public Mono<Map<String, List<PlacementTarget>>> findByPlacementIds(Collection<PlacementId> placementIds) {
        if (placementIds == null || placementIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        List<String> ids = placementIds.stream()
                .map(PlacementId::getValue)
                .toList();
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM ad_placement_targets WHERE placement_id IN (:placementIds)"
                + " ORDER BY placement_id, created_at ASC";
        return databaseClient.sql(sql)
                .bind("placementIds", ids)
                .map((row, meta) -> Map.entry(
                        row.get("placement_id", String.class),
                        PlacementTarget.of(
                                TargetType.from(row.get("target_type", String.class)),
                                row.get("target_id", String.class))))
                .all()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())))
                .map(grouped -> {
                    Map<String, List<PlacementTarget>> result = new HashMap<>(grouped);
                    for (String id : ids) {
                        result.putIfAbsent(id, List.of());
                    }
                    return result;
                });
    }

    @Override
    public Mono<Void> replaceAll(PlacementId placementId, List<PlacementTarget> targets) {
        Mono<Void> deletion = deleteByPlacementId(placementId);
        if (targets == null || targets.isEmpty()) {
            return deletion;
        }
        List<PlacementTarget> snapshot = new ArrayList<>(targets);
        return deletion.then(insertAll(placementId, snapshot));
    }

    @Override
    public Mono<Void> deleteByPlacementId(PlacementId placementId) {
        return databaseClient.sql("DELETE FROM ad_placement_targets WHERE placement_id = :placementId")
                .bind("placementId", placementId.getValue())
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> insertAll(PlacementId placementId, List<PlacementTarget> targets) {
        return Flux.fromIterable(targets)
                .concatMap(target -> databaseClient.sql("""
                        INSERT INTO ad_placement_targets (id, placement_id, target_type, target_id)
                        VALUES (:id, :placementId, :targetType, :targetId)
                        """)
                        .bind("id", UUID.randomUUID().toString())
                        .bind("placementId", placementId.getValue())
                        .bind("targetType", target.getTargetType().name())
                        .bind("targetId", target.getTargetId() == null
                                ? io.r2dbc.spi.Parameters.in(String.class)
                                : io.r2dbc.spi.Parameters.in(target.getTargetId()))
                        .fetch()
                        .rowsUpdated())
                .then();
    }
}
