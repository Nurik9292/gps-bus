package biz.ugur.busroutebackend.advertising.domain.repository;

import biz.ugur.busroutebackend.advertising.domain.model.AdDetailViewEvent;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface AdDetailViewEventRepository {
    Mono<Void> save(AdDetailViewEvent event);

    Mono<DetailViewSummary> summarizeByPlacementIdAndOccurredAtBetween(PlacementId placementId, Instant from, Instant to);
}
