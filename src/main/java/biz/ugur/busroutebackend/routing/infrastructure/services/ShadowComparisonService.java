package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorJourney;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTimetable;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.infrastructure.raptor.RaptorEngine;
import biz.ugur.busroutebackend.routing.infrastructure.raptor.RaptorTimetableCache;
import biz.ugur.busroutebackend.routing.infrastructure.services.RoutingMetrics.DiscrepancyType;
import biz.ugur.busroutebackend.routing.infrastructure.services.RoutingMetrics.Operation;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Primary
@ConditionalOnProperty(name = "routing.shadow-mode.enabled", havingValue = "true")
@Slf4j
public class ShadowComparisonService implements RouteCalculationService {

    private static final int DIRECT_STOP_LIMIT = 5;

    private final DijkstraRouteCalculationService delegate;
    private final RaptorTimetableCache raptorCache;
    private final RaptorEngine raptorEngine;
    private final RaptorJourneyMapper raptorMapper;
    private final RoutingMetrics metrics;

    @Value("${routing.shadow-mode.sample-rate:0.1}")
    private double sampleRate;

    @Value("${routing.shadow-mode.eta-diff-threshold-seconds:300}")
    private int etaDiffThresholdSeconds;

    @Value("${routing.raptor.max-rounds:4}")
    private int maxRounds;

    @Value("${routing.raptor.service-day-start:06:00}")
    private String serviceDayStart;

    public ShadowComparisonService(DijkstraRouteCalculationService delegate,
                                    RaptorTimetableCache raptorCache,
                                    RaptorEngine raptorEngine,
                                    RaptorJourneyMapper raptorMapper,
                                    RoutingMetrics metrics) {
        this.delegate = delegate;
        this.raptorCache = raptorCache;
        this.raptorEngine = raptorEngine;
        this.raptorMapper = raptorMapper;
        this.metrics = metrics;
    }

    @Override
    public Flux<BusStop> findNearbyStops(Coordinates location, double radiusKm) {
        return delegate.findNearbyStops(location, radiusKm);
    }

    @Override
    public Flux<DirectRouteResult> findDirectRoutes(List<BusStop> fromStops, List<BusStop> toStops) {
        Instant t0 = Instant.now();
        Flux<DirectRouteResult> primary = delegate.findDirectRoutes(fromStops, toStops).cache();
        primary.doOnComplete(() -> metrics.recordDuration(
                RoutingMetrics.ENGINE_DIJKSTRA, Operation.DIRECT, Duration.between(t0, Instant.now())))
                .subscribe();

        maybeRunShadow(Operation.DIRECT, primary.collectList(),
                () -> runRaptorDirect(fromStops, toStops));

        return primary;
    }

    @Override
    public Flux<TransferRouteResult> findRoutesWithOneTransfer(List<BusStop> fromStops,
                                                                List<BusStop> toStops,
                                                                double maxTransferDistanceKm) {
        Instant t0 = Instant.now();
        Flux<TransferRouteResult> primary = delegate.findRoutesWithOneTransfer(
                fromStops, toStops, maxTransferDistanceKm).cache();
        primary.doOnComplete(() -> metrics.recordDuration(
                RoutingMetrics.ENGINE_DIJKSTRA, Operation.ONE_TRANSFER, Duration.between(t0, Instant.now())))
                .subscribe();

        maybeRunShadow(Operation.ONE_TRANSFER, primary.collectList(),
                () -> runRaptorOneTransfer(fromStops, toStops));

        return primary;
    }

    @Override
    public Flux<TwoTransferRouteResult> findRoutesWithTwoTransfers(List<BusStop> fromStops,
                                                                    List<BusStop> toStops,
                                                                    double maxTransferDistanceKm) {
        Instant t0 = Instant.now();
        Flux<TwoTransferRouteResult> primary = delegate.findRoutesWithTwoTransfers(
                fromStops, toStops, maxTransferDistanceKm).cache();
        primary.doOnComplete(() -> metrics.recordDuration(
                RoutingMetrics.ENGINE_DIJKSTRA, Operation.TWO_TRANSFER, Duration.between(t0, Instant.now())))
                .subscribe();

        maybeRunShadow(Operation.TWO_TRANSFER, primary.collectList(),
                () -> runRaptorTwoTransfer(fromStops, toStops));

        return primary;
    }

    @Override
    public Mono<Boolean> areStopsConnected(BusStop stop1, BusStop stop2) {
        return delegate.areStopsConnected(stop1, stop2);
    }

    @Override
    public Flux<BusRoute> getConnectingRoutes(BusStop fromStop, BusStop toStop) {
        return delegate.getConnectingRoutes(fromStop, toStop);
    }

    private boolean shouldSampleNow() {
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    private <T> void maybeRunShadow(Operation operation,
                                     Mono<List<T>> primaryList,
                                     ShadowRun<T> shadowRun) {
        if (!shouldSampleNow()) {
            return;
        }
        Instant raptorStart = Instant.now();
        shadowRun.run()
                .collectList()
                .doOnNext(raptorList -> metrics.recordDuration(
                        RoutingMetrics.ENGINE_RAPTOR, operation,
                        Duration.between(raptorStart, Instant.now())))
                .onErrorResume(err -> {
                    log.warn("[SHADOW] raptor {} failed: {}", operation, err.getMessage());
                    metrics.recordRaptorError(operation);
                    metrics.recordDiscrepancy(operation, DiscrepancyType.RAPTOR_ERROR);
                    return Mono.just(List.of());
                })
                .zipWith(primaryList)
                .doOnNext(tuple -> compareAndPublish(operation, tuple.getT2(), tuple.getT1()))
                .doOnError(err -> log.warn("[SHADOW] compare {} failed: {}", operation, err.getMessage()))
                .subscribe();
    }

    private <T> void compareAndPublish(Operation operation, List<T> dijkstra, List<T> raptor) {
        boolean dijkstraEmpty = dijkstra.isEmpty();
        boolean raptorEmpty = raptor.isEmpty();

        if (dijkstraEmpty && !raptorEmpty) {
            metrics.recordDiscrepancy(operation, DiscrepancyType.ONLY_RAPTOR);
            log.info("[SHADOW] {} ONLY_RAPTOR: dijkstra={} raptor={}", operation, 0, raptor.size());
            return;
        }
        if (raptorEmpty && !dijkstraEmpty) {
            metrics.recordDiscrepancy(operation, DiscrepancyType.ONLY_DIJKSTRA);
            log.info("[SHADOW] {} ONLY_DIJKSTRA: dijkstra={} raptor={}", operation, dijkstra.size(), 0);
            return;
        }
        if (dijkstraEmpty) {
            return;
        }

        Set<String> dijkstraRoutes = extractRouteSet(dijkstra);
        Set<String> raptorRoutes = extractRouteSet(raptor);
        if (!dijkstraRoutes.equals(raptorRoutes)) {
            metrics.recordDiscrepancy(operation, DiscrepancyType.DIFFERENT_ROUTES);
            log.info("[SHADOW] {} DIFFERENT_ROUTES: dijkstra={} raptor={}",
                    operation, dijkstraRoutes, raptorRoutes);
            return;
        }
        Set<Integer> dijkstraDirs = extractDirectionSet(dijkstra);
        Set<Integer> raptorDirs = extractDirectionSet(raptor);
        if (!dijkstraDirs.equals(raptorDirs)) {
            metrics.recordDiscrepancy(operation, DiscrepancyType.DIFFERENT_DIRECTION);
            log.info("[SHADOW] {} DIFFERENT_DIRECTION: dijkstra={} raptor={}",
                    operation, dijkstraDirs, raptorDirs);
        }
    }

    private Set<String> extractRouteSet(List<?> results) {
        Set<String> out = new HashSet<>();
        for (Object r : results) {
            switch (r) {
                case DirectRouteResult d -> out.add(routeNumber(d.route()));
                case TransferRouteResult t -> {
                    out.add(routeNumber(t.firstRoute()));
                    out.add(routeNumber(t.secondRoute()));
                }
                case TwoTransferRouteResult t -> {
                    out.add(routeNumber(t.firstRoute()));
                    out.add(routeNumber(t.secondRoute()));
                    out.add(routeNumber(t.thirdRoute()));
                }
                default -> {}
            }
        }
        return out;
    }

    private Set<Integer> extractDirectionSet(List<?> results) {
        Set<Integer> out = new HashSet<>();
        for (Object r : results) {
            switch (r) {
                case DirectRouteResult d -> out.add(d.direction());
                case TransferRouteResult t -> {
                    out.add(t.firstDirection());
                    out.add(t.secondDirection());
                }
                case TwoTransferRouteResult t -> {
                    out.add(t.firstDirection());
                    out.add(t.secondDirection());
                    out.add(t.thirdDirection());
                }
                default -> {}
            }
        }
        return out;
    }

    private static String routeNumber(BusRoute route) {
        return route == null ? "?" : route.getRouteNumber();
    }

    private Flux<DirectRouteResult> runRaptorDirect(List<BusStop> fromStops, List<BusStop> toStops) {
        return raptorJourneys(fromStops, toStops)
                .flatMap(ctx -> Flux.fromIterable(ctx.journeys())
                        .map(j -> raptorMapper.toDirect(j, ctx.timetable()))
                        .flatMap(Mono::justOrEmpty));
    }

    private Flux<TransferRouteResult> runRaptorOneTransfer(List<BusStop> fromStops, List<BusStop> toStops) {
        return raptorJourneys(fromStops, toStops)
                .flatMap(ctx -> Flux.fromIterable(ctx.journeys())
                        .map(j -> raptorMapper.toOneTransfer(j, ctx.timetable()))
                        .flatMap(Mono::justOrEmpty));
    }

    private Flux<TwoTransferRouteResult> runRaptorTwoTransfer(List<BusStop> fromStops, List<BusStop> toStops) {
        return raptorJourneys(fromStops, toStops)
                .flatMap(ctx -> Flux.fromIterable(ctx.journeys())
                        .map(j -> raptorMapper.toTwoTransfers(j, ctx.timetable()))
                        .flatMap(Mono::justOrEmpty));
    }

    private Flux<PairContext> raptorJourneys(List<BusStop> fromStops, List<BusStop> toStops) {
        if (fromStops.isEmpty() || toStops.isEmpty()) {
            return Flux.empty();
        }
        List<BusStop> limitedFrom = fromStops.subList(0, Math.min(DIRECT_STOP_LIMIT, fromStops.size()));
        List<BusStop> limitedTo = toStops.subList(0, Math.min(DIRECT_STOP_LIMIT, toStops.size()));
        int depSec = departureTimeSec();
        return raptorCache.getTimetable()
                .flatMapMany(tt -> Flux.fromIterable(limitedFrom).flatMap(f ->
                        Flux.fromIterable(limitedTo)
                                .filter(t -> !t.getId().equals(f.getId()))
                                .map(t -> new PairContext(tt,
                                        raptorEngine.findJourneys(tt, f.getId(), t.getId(),
                                                depSec, maxRounds)))));
    }

    private int departureTimeSec() {
        LocalTime now = LocalTime.now();
        LocalTime fallback = LocalTime.parse(serviceDayStart);
        return now.isBefore(fallback) ? fallback.toSecondOfDay() : now.toSecondOfDay();
    }

    @FunctionalInterface
    private interface ShadowRun<T> {
        Flux<T> run();
    }

    private record PairContext(RaptorTimetable timetable, List<RaptorJourney> journeys) {
    }
}
