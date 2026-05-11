package biz.ugur.busroutebackend.routing.domain.model.raptor;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RaptorTimetable {

    private final Map<BusStopId, List<RaptorRoute>> routesByStop;
    private final Map<BusStopId, List<RaptorTransfer>> transfersByStop;
    private final List<RaptorRoute> allRoutes;
    private final Map<BusRouteId, BusRoute> busRoutesById;
    private final Map<BusStopId, BusStop> busStopsById;
    private final Instant builtAt;

    private RaptorTimetable(Map<BusStopId, List<RaptorRoute>> routesByStop,
                             Map<BusStopId, List<RaptorTransfer>> transfersByStop,
                             List<RaptorRoute> allRoutes,
                             Map<BusRouteId, BusRoute> busRoutesById,
                             Map<BusStopId, BusStop> busStopsById,
                             Instant builtAt) {
        this.routesByStop = routesByStop;
        this.transfersByStop = transfersByStop;
        this.allRoutes = allRoutes;
        this.busRoutesById = busRoutesById;
        this.busStopsById = busStopsById;
        this.builtAt = builtAt;
    }

    public static RaptorTimetable from(List<RaptorRoute> routes,
                                        List<RaptorTransfer> transfers) {
        return from(routes, transfers, Map.of(), Map.of());
    }

    public static RaptorTimetable from(List<RaptorRoute> routes,
                                        List<RaptorTransfer> transfers,
                                        Map<BusRouteId, BusRoute> busRoutesById,
                                        Map<BusStopId, BusStop> busStopsById) {
        Map<BusStopId, List<RaptorRoute>> routesByStop = new HashMap<>();
        for (RaptorRoute route : routes) {
            for (BusStopId stop : route.stopIds()) {
                routesByStop.computeIfAbsent(stop, k -> new java.util.ArrayList<>()).add(route);
            }
        }
        routesByStop.replaceAll((k, v) -> List.copyOf(v));

        Map<BusStopId, List<RaptorTransfer>> transfersByStop = new HashMap<>();
        for (RaptorTransfer transfer : transfers) {
            transfersByStop.computeIfAbsent(transfer.fromStopId(), k -> new java.util.ArrayList<>())
                    .add(transfer);
        }
        transfersByStop.replaceAll((k, v) -> List.copyOf(v));

        return new RaptorTimetable(
                Collections.unmodifiableMap(routesByStop),
                Collections.unmodifiableMap(transfersByStop),
                List.copyOf(routes),
                Map.copyOf(busRoutesById),
                Map.copyOf(busStopsById),
                Instant.now());
    }

    public BusRoute busRouteOf(BusRouteId id) {
        return busRoutesById.get(id);
    }

    public BusStop busStopOf(BusStopId id) {
        return busStopsById.get(id);
    }

    public List<RaptorRoute> routesAt(BusStopId stop) {
        return routesByStop.getOrDefault(stop, List.of());
    }

    public List<RaptorTransfer> transfersFrom(BusStopId stop) {
        return transfersByStop.getOrDefault(stop, List.of());
    }

    public List<RaptorRoute> allRoutes() {
        return allRoutes;
    }

    public Instant builtAt() {
        return builtAt;
    }

    public int routeCount() {
        return allRoutes.size();
    }

    public int stopCount() {
        return routesByStop.size();
    }

    public int transferCount() {
        return transfersByStop.values().stream().mapToInt(List::size).sum();
    }
}
