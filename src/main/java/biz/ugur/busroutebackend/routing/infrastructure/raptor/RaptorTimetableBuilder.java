package biz.ugur.busroutebackend.routing.infrastructure.raptor;

import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorRoute;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorStopTime;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTimetable;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTransfer;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTrip;
import biz.ugur.busroutebackend.routing.domain.model.raptor.TripId;
import biz.ugur.busroutebackend.routing.domain.repository.RaptorTimetableDataRepository;
import biz.ugur.busroutebackend.routing.domain.repository.RaptorTimetableDataRepository.RawTimetableData;
import biz.ugur.busroutebackend.routing.domain.repository.RaptorTimetableDataRepository.StopTimeRow;
import biz.ugur.busroutebackend.routing.domain.repository.RaptorTimetableDataRepository.TransferRow;
import biz.ugur.busroutebackend.routing.domain.repository.RaptorTimetableDataRepository.TripRow;
import biz.ugur.busroutebackend.transport.domain.enums.RouteDirection;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class RaptorTimetableBuilder {

    private final RaptorTimetableDataRepository dataRepository;

    public RaptorTimetableBuilder(RaptorTimetableDataRepository dataRepository) {
        this.dataRepository = dataRepository;
    }

    public Mono<RaptorTimetable> build() {
        long startNanos = System.nanoTime();
        return dataRepository.loadAll()
                .map(this::assemble)
                .doOnNext(tt -> log.info(
                        "[RAPTOR_CACHE] built timetable: routes={} stops={} transfers={} elapsedMs={}",
                        tt.routeCount(), tt.stopCount(), tt.transferCount(),
                        (System.nanoTime() - startNanos) / 1_000_000));
    }

    private RaptorTimetable assemble(RawTimetableData data) {
        Map<String, List<StopTimeRow>> stopTimesByTrip = new HashMap<>();
        for (StopTimeRow row : data.stopTimes()) {
            stopTimesByTrip.computeIfAbsent(row.tripId(), k -> new ArrayList<>()).add(row);
        }

        Map<RouteKey, List<TripRow>> tripsByRoute = new HashMap<>();
        for (TripRow trip : data.trips()) {
            tripsByRoute.computeIfAbsent(new RouteKey(trip.routeId(), trip.direction()),
                            k -> new ArrayList<>())
                    .add(trip);
        }

        List<RaptorRoute> routes = new ArrayList<>(tripsByRoute.size());
        for (Map.Entry<RouteKey, List<TripRow>> entry : tripsByRoute.entrySet()) {
            RaptorRoute built = buildRoute(entry.getKey(), entry.getValue(), stopTimesByTrip);
            if (built != null) {
                routes.add(built);
            }
        }

        List<RaptorTransfer> transfers = new ArrayList<>(data.transfers().size());
        for (TransferRow row : data.transfers()) {
            transfers.add(new RaptorTransfer(
                    BusStopId.of(row.fromStopId()),
                    BusStopId.of(row.toStopId()),
                    row.walkingSeconds(),
                    row.distanceMeters()));
        }

        return RaptorTimetable.from(routes, transfers);
    }

    private RaptorRoute buildRoute(RouteKey key,
                                    List<TripRow> tripRows,
                                    Map<String, List<StopTimeRow>> stopTimesByTrip) {
        TripRow patternTrip = tripRows.get(0);
        List<StopTimeRow> patternStops = stopTimesByTrip.get(patternTrip.tripId());
        if (patternStops == null || patternStops.size() < 2) {
            log.warn("[RAPTOR_CACHE] skip route={} dir={} — pattern trip {} has < 2 stop_times",
                    key.routeId(), key.direction(), patternTrip.tripId());
            return null;
        }
        patternStops.sort(Comparator.comparingInt(StopTimeRow::stopSequence));

        List<BusStopId> stopIds = new ArrayList<>(patternStops.size());
        List<RaptorStopTime> stopTimes = new ArrayList<>(patternStops.size());
        for (StopTimeRow row : patternStops) {
            stopIds.add(BusStopId.of(row.stopId()));
            stopTimes.add(new RaptorStopTime(row.arrivalOffsetSec(), row.departureOffsetSec()));
        }

        List<RaptorTrip> raptorTrips = new ArrayList<>();
        for (TripRow tripRow : tripRows) {
            expandFrequency(tripRow, raptorTrips);
        }
        if (raptorTrips.isEmpty()) {
            log.warn("[RAPTOR_CACHE] skip route={} dir={} — no expanded trips",
                    key.routeId(), key.direction());
            return null;
        }
        raptorTrips.sort(Comparator.comparingInt(RaptorTrip::startTimeSec));

        return new RaptorRoute(
                BusRouteId.of(key.routeId()),
                RouteDirection.fromValue(key.direction()),
                stopIds,
                stopTimes,
                raptorTrips);
    }

    private void expandFrequency(TripRow tripRow, List<RaptorTrip> out) {
        int startSec = tripRow.startTime().toSecondOfDay();
        int endSec = tripRow.endTime().toSecondOfDay();
        Integer headway = tripRow.headwaySeconds();

        if (headway == null) {
            out.add(new RaptorTrip(TripId.of(tripRow.tripId()), startSec));
            return;
        }
        if (headway <= 0) {
            log.warn("[RAPTOR_CACHE] trip {} has non-positive headway {}, treating as single departure",
                    tripRow.tripId(), headway);
            out.add(new RaptorTrip(TripId.of(tripRow.tripId()), startSec));
            return;
        }
        for (int t = startSec; t <= endSec; t += headway) {
            out.add(new RaptorTrip(TripId.of(tripRow.tripId()), t));
        }
    }

    private record RouteKey(String routeId, int direction) {
    }
}
