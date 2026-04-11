package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitGraph;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitPath;
import biz.ugur.busroutebackend.routing.domain.model.graph.TransitPathSegment;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.infrastructure.graph.DijkstraEngine;
import biz.ugur.busroutebackend.routing.infrastructure.graph.TransitGraphCache;
import biz.ugur.busroutebackend.routing.infrastructure.services.query.NearbyStopsQueryService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "routing.dijkstra.enabled", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class DijkstraRouteCalculationService implements RouteCalculationService {

    private final TransitGraphCache graphCache;
    private final DijkstraEngine dijkstraEngine;
    private final NearbyStopsQueryService nearbyStopsQueryService;

    @Override
    public Flux<BusStop> findNearbyStops(Coordinates location, double radiusKm) {
        return nearbyStopsQueryService.findStopsWithinRadius(location, radiusKm);
    }

    private static final int DIRECT_STOP_LIMIT = 5;

    @Override
    public Flux<DirectRouteResult> findDirectRoutes(List<BusStop> fromStops, List<BusStop> toStops) {
        if (fromStops.isEmpty() || toStops.isEmpty()) return Flux.empty();

        List<BusStop> limitedFrom = fromStops.subList(0, Math.min(DIRECT_STOP_LIMIT, fromStops.size()));
        List<BusStop> limitedTo = toStops.subList(0, Math.min(DIRECT_STOP_LIMIT, toStops.size()));
        log.info("Dijkstra direct: {}×{} stop pairs ({} from, {} to truncated to {})",
                limitedFrom.size(), limitedTo.size(), fromStops.size(), toStops.size(), DIRECT_STOP_LIMIT);

        return graphCache.getGraph()
                .flatMap(graph -> Mono.fromCallable(() -> {
                    long start = System.currentTimeMillis();
                    List<DirectRouteResult> results = new ArrayList<>();
                    Set<String> seen = new HashSet<>();

                    for (BusStop fromStop : limitedFrom) {
                        for (BusStop toStop : limitedTo) {
                            String fromId = fromStop.getId().getValue();
                            String toId = toStop.getId().getValue();

                            for (TransitPath path : dijkstraEngine.findPaths(graph, fromId, toId, 2)) {
                                List<TransitPathSegment> collapsed = path.collapsed();
                                List<TransitPathSegment> busSegs = busSegments(collapsed);

                                if (busSegs.size() == 1) {
                                    TransitPathSegment seg = busSegs.get(0);
                                    BusRoute route = graph.getRoute(seg.routeId());
                                    BusStop boarding = graph.getStop(seg.fromStopId());
                                    BusStop alighting = graph.getStop(seg.toStopId());

                                    if (route == null || boarding == null || alighting == null) continue;

                                    String key = seg.routeId() + ":" + seg.fromStopId() + ":" + seg.toStopId();
                                    if (seen.add(key)) {
                                        results.add(new DirectRouteResult(
                                                route, boarding, alighting,
                                                seg.costMinutes(),
                                                leadingWalkKm(collapsed),
                                                trailingWalkKm(collapsed)
                                        ));
                                    }
                                }
                            }
                        }
                    }

                    log.info("Dijkstra direct routes: {} results in {}ms", results.size(),
                            System.currentTimeMillis() - start);
                    return results;
                }).subscribeOn(Schedulers.boundedElastic()))
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<TransferRouteResult> findRoutesWithOneTransfer(List<BusStop> fromStops,
                                                               List<BusStop> toStops,
                                                               double maxTransferDistanceKm) {
        if (fromStops.isEmpty() || toStops.isEmpty()) return Flux.empty();

        List<BusStop> limitedFrom = fromStops.subList(0, Math.min(DIRECT_STOP_LIMIT, fromStops.size()));
        List<BusStop> limitedTo = toStops.subList(0, Math.min(DIRECT_STOP_LIMIT, toStops.size()));
        log.info("Dijkstra one-transfer: {}×{} stop pairs", limitedFrom.size(), limitedTo.size());

        return graphCache.getGraph()
                .flatMap(graph -> Mono.fromCallable(() -> {
                    long start = System.currentTimeMillis();
                    List<TransferRouteResult> results = new ArrayList<>();
                    Set<String> seen = new HashSet<>();

                    for (BusStop fromStop : limitedFrom) {
                        for (BusStop toStop : limitedTo) {
                            String fromId = fromStop.getId().getValue();
                            String toId = toStop.getId().getValue();

                            for (TransitPath path : dijkstraEngine.findPaths(graph, fromId, toId)) {
                                List<TransitPathSegment> collapsed = path.collapsed();
                                List<TransitPathSegment> busSegs = busSegments(collapsed);

                                if (busSegs.size() == 2) {
                                    TransitPathSegment first = busSegs.get(0);
                                    TransitPathSegment second = busSegs.get(1);

                                    BusRoute firstRoute = graph.getRoute(first.routeId());
                                    BusRoute secondRoute = graph.getRoute(second.routeId());
                                    BusStop boarding = graph.getStop(first.fromStopId());
                                    BusStop transferStop = graph.getStop(first.toStopId());
                                    BusStop alighting = graph.getStop(second.toStopId());

                                    if (anyNull(firstRoute, secondRoute, boarding, transferStop, alighting)) continue;

                                    String key = first.routeId() + ":" + second.routeId() + ":" + first.toStopId();
                                    if (seen.add(key)) {
                                        results.add(new TransferRouteResult(
                                                firstRoute, boarding, transferStop,
                                                secondRoute, alighting,
                                                first.costMinutes(),
                                                transferWaitMinutes(collapsed, first, second),
                                                second.costMinutes(),
                                                leadingWalkKm(collapsed),
                                                trailingWalkKm(collapsed)
                                        ));
                                    }
                                }
                            }
                        }
                    }

                    log.info("Dijkstra one-transfer routes: {} results in {}ms", results.size(),
                            System.currentTimeMillis() - start);
                    return results;
                }).subscribeOn(Schedulers.boundedElastic()))
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<TwoTransferRouteResult> findRoutesWithTwoTransfers(List<BusStop> fromStops,
                                                                   List<BusStop> toStops,
                                                                   double maxTransferDistanceKm) {
        if (fromStops.isEmpty() || toStops.isEmpty()) return Flux.empty();

        List<BusStop> limitedFrom = fromStops.subList(0, Math.min(DIRECT_STOP_LIMIT, fromStops.size()));
        List<BusStop> limitedTo = toStops.subList(0, Math.min(DIRECT_STOP_LIMIT, toStops.size()));
        log.info("Dijkstra two-transfer: {}×{} stop pairs", limitedFrom.size(), limitedTo.size());

        return graphCache.getGraph()
                .flatMap(graph -> Mono.fromCallable(() -> {
                    long start = System.currentTimeMillis();
                    List<TwoTransferRouteResult> results = new ArrayList<>();
                    Set<String> seen = new HashSet<>();

                    for (BusStop fromStop : limitedFrom) {
                        for (BusStop toStop : limitedTo) {
                            String fromId = fromStop.getId().getValue();
                            String toId = toStop.getId().getValue();

                            for (TransitPath path : dijkstraEngine.findPaths(graph, fromId, toId)) {
                                List<TransitPathSegment> collapsed = path.collapsed();
                                List<TransitPathSegment> busSegs = busSegments(collapsed);

                                if (busSegs.size() == 3) {
                                    TransitPathSegment first = busSegs.get(0);
                                    TransitPathSegment second = busSegs.get(1);
                                    TransitPathSegment third = busSegs.get(2);

                                    BusRoute firstRoute = graph.getRoute(first.routeId());
                                    BusRoute secondRoute = graph.getRoute(second.routeId());
                                    BusRoute thirdRoute = graph.getRoute(third.routeId());
                                    BusStop boarding = graph.getStop(first.fromStopId());
                                    BusStop firstTransfer = graph.getStop(first.toStopId());
                                    BusStop secondTransfer = graph.getStop(second.toStopId());
                                    BusStop alighting = graph.getStop(third.toStopId());

                                    if (anyNull(firstRoute, secondRoute, thirdRoute,
                                            boarding, firstTransfer, secondTransfer, alighting)) continue;

                                    String key = first.routeId() + ":" + second.routeId() + ":" + third.routeId()
                                            + ":" + first.toStopId() + ":" + second.toStopId();
                                    if (seen.add(key)) {
                                        results.add(new TwoTransferRouteResult(
                                                firstRoute, boarding, firstTransfer,
                                                secondRoute, secondTransfer,
                                                thirdRoute, alighting,
                                                first.costMinutes(),
                                                transferWaitMinutes(collapsed, first, second),
                                                second.costMinutes(),
                                                transferWaitMinutes(collapsed, second, third),
                                                third.costMinutes(),
                                                leadingWalkKm(collapsed),
                                                trailingWalkKm(collapsed)
                                        ));
                                    }
                                }
                            }
                        }
                    }

                    log.info("Dijkstra two-transfer routes: {} results in {}ms", results.size(),
                            System.currentTimeMillis() - start);
                    return results;
                }).subscribeOn(Schedulers.boundedElastic()))
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Boolean> areStopsConnected(BusStop stop1, BusStop stop2) {
        return graphCache.getGraph().map(graph -> {
            List<TransitPath> paths = dijkstraEngine.findPaths(
                    graph, stop1.getId().getValue(), stop2.getId().getValue());
            return !paths.isEmpty();
        });
    }

    @Override
    public Flux<BusRoute> getConnectingRoutes(BusStop fromStop, BusStop toStop) {
        return graphCache.getGraph().flatMapMany(graph -> {
            List<TransitPath> paths = dijkstraEngine.findPaths(
                    graph, fromStop.getId().getValue(), toStop.getId().getValue());

            Set<String> seen = new HashSet<>();
            List<BusRoute> routes = new ArrayList<>();
            for (TransitPath path : paths) {
                for (String routeId : path.usedRouteIds()) {
                    if (seen.add(routeId)) {
                        BusRoute route = graph.getRoute(routeId);
                        if (route != null) routes.add(route);
                    }
                }
            }
            return Flux.fromIterable(routes);
        });
    }

   
    private List<TransitPathSegment> busSegments(List<TransitPathSegment> collapsed) {
        return collapsed.stream().filter(TransitPathSegment::isBusRide).toList();
    }


    private double leadingWalkKm(List<TransitPathSegment> collapsed) {
        if (!collapsed.isEmpty() && collapsed.get(0).isWalking()) {
            return minutesToKm(collapsed.get(0).costMinutes());
        }
        return 0.0;
    }


    private double trailingWalkKm(List<TransitPathSegment> collapsed) {
        if (!collapsed.isEmpty() && collapsed.get(collapsed.size() - 1).isWalking()) {
            return minutesToKm(collapsed.get(collapsed.size() - 1).costMinutes());
        }
        return 0.0;
    }


    private int transferWaitMinutes(List<TransitPathSegment> collapsed,
                                    TransitPathSegment firstBus, TransitPathSegment secondBus) {
        int firstIdx = -1, secondIdx = -1;
        for (int i = 0; i < collapsed.size(); i++) {
            TransitPathSegment seg = collapsed.get(i);
            if (firstIdx < 0 && seg.isBusRide() && Objects.equals(seg.routeId(), firstBus.routeId())) {
                firstIdx = i;
            } else if (firstIdx >= 0 && seg.isBusRide() && Objects.equals(seg.routeId(), secondBus.routeId())) {
                secondIdx = i;
                break;
            }
        }
        if (firstIdx < 0 || secondIdx < 0) return 2;

        int walkTime = 0;
        for (int i = firstIdx + 1; i < secondIdx; i++) {
            walkTime += collapsed.get(i).costMinutes();
        }
        return Math.max(2, walkTime);
    }

    private double minutesToKm(int minutes) {
        return minutes * 5.0 / 60.0;
    }

    private boolean anyNull(Object... objects) {
        for (Object o : objects) {
            if (o == null) return true;
        }
        return false;
    }
}
