package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorJourney;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorLeg;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTimetable;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.infrastructure.raptor.RaptorEngine;
import biz.ugur.busroutebackend.routing.infrastructure.raptor.RaptorTimetableCache;
import biz.ugur.busroutebackend.routing.infrastructure.services.query.NearbyStopsQueryService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Primary
@ConditionalOnProperty(name = "routing.raptor.enabled", havingValue = "true")
@Slf4j
public class RaptorRouteCalculationService implements RouteCalculationService {

    private static final int DIRECT_STOP_LIMIT = 5;

    private final RaptorTimetableCache cache;
    private final RaptorEngine engine;
    private final RaptorJourneyMapper mapper;
    private final NearbyStopsQueryService nearbyStopsQueryService;

    @Value("${routing.raptor.max-rounds:4}")
    private int maxRounds;

    @Value("${routing.raptor.service-day-start:06:00}")
    private String serviceDayStart;

    public RaptorRouteCalculationService(RaptorTimetableCache cache,
                                          RaptorEngine engine,
                                          RaptorJourneyMapper mapper,
                                          NearbyStopsQueryService nearbyStopsQueryService) {
        this.cache = cache;
        this.engine = engine;
        this.mapper = mapper;
        this.nearbyStopsQueryService = nearbyStopsQueryService;
    }

    @Override
    public Flux<BusStop> findNearbyStops(Coordinates location, double radiusKm) {
        return nearbyStopsQueryService.findStopsWithinRadius(location, radiusKm);
    }

    @Override
    public Flux<DirectRouteResult> findDirectRoutes(List<BusStop> fromStops, List<BusStop> toStops) {
        return runForPairs(fromStops, toStops, "direct")
                .flatMap(ctx -> Flux.fromIterable(ctx.journeys())
                        .map(j -> mapper.toDirect(j, ctx.timetable()))
                        .flatMap(Mono::justOrEmpty))
                .distinct(key -> directKey((DirectRouteResult) key));
    }

    @Override
    public Flux<TransferRouteResult> findRoutesWithOneTransfer(List<BusStop> fromStops,
                                                                List<BusStop> toStops,
                                                                double maxTransferDistanceKm) {
        return runForPairs(fromStops, toStops, "one-transfer")
                .flatMap(ctx -> Flux.fromIterable(ctx.journeys())
                        .map(j -> mapper.toOneTransfer(j, ctx.timetable()))
                        .flatMap(Mono::justOrEmpty))
                .distinct(key -> transferKey((TransferRouteResult) key));
    }

    @Override
    public Flux<TwoTransferRouteResult> findRoutesWithTwoTransfers(List<BusStop> fromStops,
                                                                    List<BusStop> toStops,
                                                                    double maxTransferDistanceKm) {
        return runForPairs(fromStops, toStops, "two-transfer")
                .flatMap(ctx -> Flux.fromIterable(ctx.journeys())
                        .map(j -> mapper.toTwoTransfers(j, ctx.timetable()))
                        .flatMap(Mono::justOrEmpty))
                .distinct(key -> twoTransferKey((TwoTransferRouteResult) key));
    }

    @Override
    public Mono<Boolean> areStopsConnected(BusStop stop1, BusStop stop2) {
        return cache.getTimetable()
                .map(tt -> !engine.findJourneys(tt, stop1.getId(), stop2.getId(),
                        departureTimeSec(), maxRounds).isEmpty());
    }

    @Override
    public Flux<BusRoute> getConnectingRoutes(BusStop fromStop, BusStop toStop) {
        return cache.getTimetable()
                .flatMapMany(tt -> {
                    List<RaptorJourney> journeys = engine.findJourneys(
                            tt, fromStop.getId(), toStop.getId(), departureTimeSec(), maxRounds);
                    Set<String> seen = new HashSet<>();
                    List<BusRoute> routes = new ArrayList<>();
                    for (RaptorJourney j : journeys) {
                        for (RaptorLeg leg : j.legs()) {
                            if (leg.type() != RaptorLeg.LegType.BUS) continue;
                            String routeIdKey = leg.routeId().getValue();
                            if (seen.add(routeIdKey)) {
                                BusRoute route = tt.busRouteOf(leg.routeId());
                                if (route != null) routes.add(route);
                            }
                        }
                    }
                    return Flux.fromIterable(routes);
                });
    }

    private Flux<PairContext> runForPairs(List<BusStop> fromStops, List<BusStop> toStops, String label) {
        if (fromStops.isEmpty() || toStops.isEmpty()) {
            return Flux.empty();
        }
        List<BusStop> limitedFrom = fromStops.subList(0, Math.min(DIRECT_STOP_LIMIT, fromStops.size()));
        List<BusStop> limitedTo = toStops.subList(0, Math.min(DIRECT_STOP_LIMIT, toStops.size()));
        log.info("[RAPTOR] {} search: {}x{} pairs", label, limitedFrom.size(), limitedTo.size());

        int depSec = departureTimeSec();
        return cache.getTimetable()
                .flatMapMany(tt -> Flux.fromIterable(allPairs(limitedFrom, limitedTo))
                        .map(pair -> new PairContext(tt,
                                engine.findJourneys(tt, pair.from().getId(), pair.to().getId(),
                                        depSec, maxRounds))));
    }

    private List<StopPair> allPairs(List<BusStop> from, List<BusStop> to) {
        List<StopPair> pairs = new ArrayList<>(from.size() * to.size());
        for (BusStop f : from) {
            for (BusStop t : to) {
                if (!f.getId().equals(t.getId())) {
                    pairs.add(new StopPair(f, t));
                }
            }
        }
        return pairs;
    }

    private int departureTimeSec() {
        LocalTime now = LocalTime.now();
        LocalTime fallback = LocalTime.parse(serviceDayStart);
        return now.isBefore(fallback) ? fallback.toSecondOfDay() : now.toSecondOfDay();
    }

    private String directKey(DirectRouteResult r) {
        return r.route().getId().getValue() + ":" + r.fromStop().getId().getValue()
                + ":" + r.toStop().getId().getValue() + ":" + r.direction();
    }

    private String transferKey(TransferRouteResult r) {
        return r.firstRoute().getId().getValue() + ":" + r.secondRoute().getId().getValue()
                + ":" + r.fromStop().getId().getValue() + ":" + r.transferStop().getId().getValue()
                + ":" + r.toStop().getId().getValue();
    }

    private String twoTransferKey(TwoTransferRouteResult r) {
        return r.firstRoute().getId().getValue() + ":" + r.secondRoute().getId().getValue()
                + ":" + r.thirdRoute().getId().getValue()
                + ":" + r.fromStop().getId().getValue() + ":" + r.toStop().getId().getValue();
    }

    private record StopPair(BusStop from, BusStop to) {
    }

    private record PairContext(RaptorTimetable timetable, List<RaptorJourney> journeys) {
    }
}
