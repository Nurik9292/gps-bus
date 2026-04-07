package biz.ugur.busroutebackend.interfaces.rest.transport.V1.controller;

import biz.ugur.busroutebackend.admin.domain.exceptions.BusStopException;
import biz.ugur.busroutebackend.interfaces.rest.transport.V1.response.BusStopArrivalsResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.dto.NearbyStopArrivalsResponse;
import biz.ugur.busroutebackend.transport.infrastructure.services.BusStopRealTimeServiceImpl;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_STOPS;

@RestController
@RequestMapping(V1_STOPS)
@CrossOrigin(origins = "*")
public class BusStopRealTimeController extends BaseController {

    private final BusStopRealTimeServiceImpl busStopRealTimeService;

    public BusStopRealTimeController(BusStopRealTimeServiceImpl busStopRealTimeService,
                                    MessageSource messageSource) {
        super(messageSource);
        this.busStopRealTimeService = busStopRealTimeService;
    }

    @Override
    protected String getControllerName() {
        return BusStopRealTimeController.class.getSimpleName();
    }

    @GetMapping("/{stopId}/arrivals")
    public Mono<ResponseEntity<ApiResponse<BusStopArrivalsResponse>>> getStopArrivals(@PathVariable String stopId) {
        return busStopRealTimeService.getStopArrivals(stopId)
                .onErrorReturn(BusStopException.class,
                        new BusStopArrivalsResponse(stopId, null, 0.0, 0.0, Collections.emptyList(), LocalDateTime.now()))
                .flatMap(data -> Mono.just(ResponseEntity.ok(ApiResponse.success(data))));
    }

    @GetMapping("/nearby/arrivals")
    public Mono<ResponseEntity<ApiResponse<List<NearbyStopArrivalsResponse>>>> getNearbyStopArrivals(
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam(defaultValue = "500") Integer radiusMeters) {

        return okList(busStopRealTimeService.getNearbyStopArrivals(lat, lon, radiusMeters));
    }

    @GetMapping("/{stopId}/arrivals/stream")
    public Flux<ServerSentEvent<BusStopArrivalsResponse>> streamStopArrivals(@PathVariable String stopId) {
        return busStopRealTimeService.streamStopArrivals(stopId)
                .map(arrivals -> ServerSentEvent.<BusStopArrivalsResponse>builder()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .event("bus-arrivals")
                        .data(arrivals)
                        .build());
    }
}