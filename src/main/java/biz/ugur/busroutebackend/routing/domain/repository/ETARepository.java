package biz.ugur.busroutebackend.routing.domain.repository;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ETARepository {

    Mono<Integer> getVehicleBasedWaitingTime(String routeNumber, String stopName);

    Mono<Integer> getStatisticalWaitingTime(String routeNumber, LocalDateTime currentTime);

    Mono<Integer> calculateTravelTimeFromDatabase(String routeNumber, String fromStopName, String toStopName);
}
