package biz.ugur.busroutebackend.routing.domain.repository;

import biz.ugur.busroutebackend.routing.domain.model.raptor.StopTime;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface StopTimeRepository {

    Flux<StopTime> saveAll(List<StopTime> stopTimes);

    Mono<Long> count();

    Mono<Long> deleteAll();
}
