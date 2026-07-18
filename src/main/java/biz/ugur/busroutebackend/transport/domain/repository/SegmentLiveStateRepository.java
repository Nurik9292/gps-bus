package biz.ugur.busroutebackend.transport.domain.repository;

import reactor.core.publisher.Mono;

import java.time.Instant;

public interface SegmentLiveStateRepository {

    Mono<Void> recordTravel(String fromStopId, String toStopId, double seconds, Instant observedAt);
}
