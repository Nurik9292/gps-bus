package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentLiveSnapshot;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface SegmentLiveStateRepository {

    Mono<Void> recordTravel(String fromStopId, String toStopId, double seconds, Instant observedAt);

    Flux<SegmentLiveSnapshot> scanLiveEdges();
}
