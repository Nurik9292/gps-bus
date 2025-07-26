package biz.ugur.busroutebackend.transport.domain.services;

import biz.ugur.busroutebackend.transport.application.dto.BusStopArrivalsResponse;
import biz.ugur.busroutebackend.transport.application.dto.NearbyStopArrivalsResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BusStopRealTimeService {

    Mono<BusStopArrivalsResponse> getStopArrivals(String stopId);

    Flux<NearbyStopArrivalsResponse> getNearbyStopArrivals(Double lat, Double lon, Integer radiusMeters);

    Flux<BusStopArrivalsResponse> streamStopArrivals(String stopId);
}
