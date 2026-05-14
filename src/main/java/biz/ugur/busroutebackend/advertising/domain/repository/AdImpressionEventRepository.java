package biz.ugur.busroutebackend.advertising.domain.repository;

import biz.ugur.busroutebackend.advertising.domain.model.AdImpressionEvent;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface AdImpressionEventRepository {
    Mono<Void> save(AdImpressionEvent event);

    Mono<Long> countByPlacementIdAndOccurredAtBetween(PlacementId placementId, Instant from, Instant to);
}
