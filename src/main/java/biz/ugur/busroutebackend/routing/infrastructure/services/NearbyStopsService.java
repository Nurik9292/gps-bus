package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.StopsContext;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class NearbyStopsService {

    private final RouteCalculationService routeCalculationService;

    private static final double SEARCH_RADIUS_KM = 0.8;
    private static final int MAX_STOPS_PER_LOCATION = 8;

    public NearbyStopsService(RouteCalculationService routeCalculationService) {
        this.routeCalculationService = routeCalculationService;
    }

    public Mono<StopsContext> findStopsForBothLocations(SearchContext context) {
        return Mono.zip(
                        findNearbyStops(context.fromLocation(), "origin"),
                        findNearbyStops(context.toLocation(), "destination")
                ).map(tuple -> new StopsContext(tuple.getT1(), tuple.getT2()))
                .doOnNext(stopsContext -> logStopsFound(context, stopsContext));
    }

    private Mono<List<BusStop>> findNearbyStops(Location location, String locationType) {
        return routeCalculationService.findNearbyStops(location, SEARCH_RADIUS_KM)
                .filter(this::isStopAccessible)
                .sort((stop1, stop2) -> compareStopsByPriority(stop1, stop2, location))
                .take(MAX_STOPS_PER_LOCATION)
                .collectList()
                .doOnNext(stops -> log.debug("Found {} {} stops", stops.size(), locationType));
    }

    private boolean isStopAccessible(BusStop stop) {
        return stop.getIsActive() &&
                stop.getLatitude() != null &&
                stop.getLongitude() != null;
    }

    private int compareStopsByPriority(BusStop stop1, BusStop stop2, Location location) {
        int priorityCompare = Boolean.compare(stop2.getIsMajorStop(), stop1.getIsMajorStop());
        if (priorityCompare != 0) return priorityCompare;

        double dist1 = location.distanceTo(stop1.getLatitude().doubleValue(), stop1.getLongitude().doubleValue());
        double dist2 = location.distanceTo(stop2.getLatitude().doubleValue(), stop2.getLongitude().doubleValue());
        return Double.compare(dist1, dist2);
    }

    private void logStopsFound(SearchContext context, StopsContext stopsContext) {
        log.debug("[{}] Found {} origin and {} destination stops",
                context.searchId(),
                stopsContext.fromStops().size(),
                stopsContext.toStops().size());
    }
}
