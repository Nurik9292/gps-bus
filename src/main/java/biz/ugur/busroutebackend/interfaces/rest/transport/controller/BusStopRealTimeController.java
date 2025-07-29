package biz.ugur.busroutebackend.interfaces.rest.transport.controller;

import biz.ugur.busroutebackend.interfaces.rest.transport.dto.response.BusStopArrivalsResponse;
import biz.ugur.busroutebackend.transport.application.dto.NearbyStopArrivalsResponse;
import biz.ugur.busroutebackend.transport.infrastructure.services.BusStopRealTimeServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/stops")
@Slf4j
public class BusStopRealTimeController {

    private final BusStopRealTimeServiceImpl busStopRealTimeService;

    public BusStopRealTimeController(BusStopRealTimeServiceImpl busStopRealTimeService) {
        this.busStopRealTimeService = busStopRealTimeService;
    }

    @GetMapping("/{stopId}/arrivals")
    public Mono<BusStopArrivalsResponse> getStopArrivals(@PathVariable String stopId) {
        log.info("Getting real-time arrivals for stop: {}", stopId);

        return busStopRealTimeService.getStopArrivals(stopId)
                .doOnNext(response -> log.debug("Found {} arriving buses for stop {}",
                        response.getArrivals().size(), stopId));
    }

    @GetMapping("/nearby/arrivals")
    public Flux<NearbyStopArrivalsResponse> getNearbyStopArrivals(
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam(defaultValue = "500") Integer radiusMeters) {

        log.info("Getting arrivals for nearest stop to ({}, {}) within {}m", lat, lon, radiusMeters);

        return busStopRealTimeService.getNearbyStopArrivals(lat, lon, radiusMeters);
    }

    @GetMapping("/{stopId}/arrivals/stream")
    public Flux<ServerSentEvent<BusStopArrivalsResponse>> streamStopArrivals(@PathVariable String stopId) {
        log.info("Starting real-time stream for stop: {}", stopId);

        return busStopRealTimeService.streamStopArrivals(stopId)
                .map(arrivals -> ServerSentEvent.<BusStopArrivalsResponse>builder()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .event("bus-arrivals")
                        .data(arrivals)
                        .build())
                .doOnSubscribe(sub -> log.debug("Client subscribed to stop {} arrivals", stopId))
                .doOnCancel(() -> log.debug("Client unsubscribed from stop {} arrivals", stopId));
    }
}