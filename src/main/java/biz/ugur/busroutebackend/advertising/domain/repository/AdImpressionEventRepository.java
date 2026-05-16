package biz.ugur.busroutebackend.advertising.domain.repository;

import biz.ugur.busroutebackend.advertising.domain.model.AdImpressionEvent;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public interface AdImpressionEventRepository {
    Mono<Void> save(AdImpressionEvent event);

    Mono<Long> countByPlacementIdAndOccurredAtBetween(PlacementId placementId, Instant from, Instant to);

    Mono<Map<LocalDate, Long>> countByDayBetween(PlacementId placementId, Instant from, Instant to);

    Mono<Long> countByOccurredAtBetween(Instant from, Instant to);
}
