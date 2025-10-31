package biz.ugur.busroutebackend.routing.domain.repository;

import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.DirectRouteResult;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.TransferRouteResult;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.TwoTransferRouteResult;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import reactor.core.publisher.Flux;

import java.util.List;

public interface RouteSearchRepository {

    Flux<DirectRouteResult> findDirectRoutes(
            List<BusStop> fromStops,
            List<BusStop> toStops
    );

    Flux<TransferRouteResult> findOneTransferRoutes(
            List<BusStop> fromStops,
            List<BusStop> toStops,
            double maxTransferDistanceKm
    );

    Flux<TwoTransferRouteResult> findTwoTransferRoutes(
            List<BusStop> fromStops,
            List<BusStop> toStops,
            double maxTransferDistanceKm
    );
}
