package biz.ugur.busroutebackend.routing.domain.services;

import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface RouteCalculationService {

    Flux<BusStop> findNearbyStops(Location location, double radiusKm);

    Flux<DirectRouteResult> findDirectRoutes(List<BusStop> fromStops, List<BusStop> toStops);

    Flux<TransferRouteResult> findRoutesWithOneTransfer(List<BusStop> fromStops, List<BusStop> toStops,
                                                        double maxTransferDistanceKm);

    Flux<TwoTransferRouteResult> findRoutesWithTwoTransfers(List<BusStop> fromStops, List<BusStop> toStops,
                                                            double maxTransferDistanceKm);

    Mono<Boolean> areStopsConnected(BusStop stop1, BusStop stop2);

    Flux<BusRoute> getConnectingRoutes(BusStop fromStop, BusStop toStop);


    record DirectRouteResult(
            BusRoute route,
            BusStop fromStop,
            BusStop toStop,
            int estimatedTravelMinutes,
            double walkingDistanceToStart,
            double walkingDistanceFromEnd
    ) {}

    record TransferRouteResult(
            BusRoute firstRoute,
            BusStop fromStop,
            BusStop transferStop,
            BusRoute secondRoute,
            BusStop toStop,
            int firstRouteTravelMinutes,
            int transferWaitMinutes,
            int secondRouteTravelMinutes,
            double walkingDistanceToStart,
            double walkingDistanceFromEnd
    ) {}

    record TwoTransferRouteResult(
            BusRoute firstRoute,
            BusStop fromStop,
            BusStop firstTransferStop,
            BusRoute secondRoute,
            BusStop secondTransferStop,
            BusRoute thirdRoute,
            BusStop toStop,
            int firstRouteTravelMinutes,
            int firstTransferWaitMinutes,
            int secondRouteTravelMinutes,
            int secondTransferWaitMinutes,
            int thirdRouteTravelMinutes,
            double walkingDistanceToStart,
            double walkingDistanceFromEnd
    ) {}
}