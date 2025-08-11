package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopDetail;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopsData;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopsStatistics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;


public interface RouteStopRepository {

    Mono<Void> deleteExistingStops(String routeId);

    Mono<Void> insertRouteStop(String routeId, String stopId, int sequence, int direction);

    Flux<RouteStopInfo> getRouteStops(String routeId, int direction);

    Flux<RouteStopDetail> getRouteStopsDetail(String routeId, int direction);

    Mono<List<RouteStopDetail>> getForwardStopsDetail(String routeId);

    Mono<List<RouteStopDetail>> getBackwardStopsDetail(String routeId);

    Mono<RouteStopsData> getAllRouteStopsData(String routeId);

    Mono<RouteStopsStatistics> getRouteStopsStatistics(String routeId);
}
