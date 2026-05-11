package biz.ugur.busroutebackend.routing.domain.repository;

import biz.ugur.busroutebackend.routing.domain.model.raptor.Trip;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface TripRepository {

    Mono<Trip> save(Trip trip);

    Flux<Trip> saveAll(List<Trip> trips);

    Mono<Long> count();

    Mono<Long> deleteAll();
}
