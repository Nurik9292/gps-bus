package biz.ugur.busroutebackend.advertising.domain.repository;

import biz.ugur.busroutebackend.advertising.domain.model.AdDetailViewEvent;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public interface AdDetailViewEventRepository {
    Mono<Void> save(AdDetailViewEvent event);

    Mono<DetailViewSummary> summarizeByPlacementIdAndOccurredAtBetween(PlacementId placementId, Instant from, Instant to);

    Mono<Map<LocalDate, DetailViewSummary>> summarizeByDayBetween(PlacementId placementId, Instant from, Instant to);
}
