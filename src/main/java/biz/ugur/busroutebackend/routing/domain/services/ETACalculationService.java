package biz.ugur.busroutebackend.routing.domain.services;

import biz.ugur.busroutebackend.routing.domain.valueobjects.Location;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ETACalculationService {

    Mono<LocalDateTime> calculateEstimatedArrival(String routeNumber, String fromStopName,
                                                  String toStopName, LocalDateTime departureTime);

    int calculateWalkingTimeMinutes(Location from, Location to);

    Mono<Integer> calculateWaitingTimeMinutes(String routeNumber, String stopName, LocalDateTime currentTime);

    Mono<Integer> calculateTravelTimeMinutes(String routeNumber, String fromStopName, String toStopName);

    int calculateTransferTimeMinutes(String stopName, boolean isMajorStop);
}