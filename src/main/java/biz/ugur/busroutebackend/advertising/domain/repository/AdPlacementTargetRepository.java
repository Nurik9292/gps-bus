package biz.ugur.busroutebackend.advertising.domain.repository;

import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface AdPlacementTargetRepository {

    Flux<PlacementTarget> findByPlacementId(PlacementId placementId);

    Mono<Map<String, List<PlacementTarget>>> findByPlacementIds(Collection<PlacementId> placementIds);

    Mono<Void> replaceAll(PlacementId placementId, List<PlacementTarget> targets);

    Mono<Void> deleteByPlacementId(PlacementId placementId);
}
