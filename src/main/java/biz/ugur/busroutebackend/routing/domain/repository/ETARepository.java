package biz.ugur.busroutebackend.routing.domain.repository;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ETARepository {

    Mono<Integer> getVehicleBasedWaitingTime(String routeId, String stopName);

    Mono<Integer> getStatisticalWaitingTime(String routeId, LocalDateTime currentTime);

    Mono<Integer> calculateTravelTimeFromDatabase(String routeId, String fromStopName, String toStopName);

    Mono<Integer> countStopsBetween(String routeId, String fromStopName, String toStopName);
}
