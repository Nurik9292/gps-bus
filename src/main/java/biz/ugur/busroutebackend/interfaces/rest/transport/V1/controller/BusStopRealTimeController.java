package biz.ugur.busroutebackend.interfaces.rest.transport.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.transport.V1.response.BusStopArrivalsResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.dto.NearbyStopArrivalsResponse;
import biz.ugur.busroutebackend.transport.infrastructure.services.BusStopRealTimeServiceImpl;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.API_V1;

@RestController
@RequestMapping(API_V1 + "/stops")
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
        return ok(busStopRealTimeService.getStopArrivals(stopId));
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