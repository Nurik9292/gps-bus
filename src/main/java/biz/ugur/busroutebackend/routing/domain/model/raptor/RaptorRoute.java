package biz.ugur.busroutebackend.routing.domain.model.raptor;

import biz.ugur.busroutebackend.transport.domain.enums.RouteDirection;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RaptorRoute {

    private final BusRouteId routeId;
    private final RouteDirection direction;
    private final List<BusStopId> stopIds;
    private final List<RaptorStopTime> stopTimes;
    private final List<RaptorTrip> trips;
    private final Map<BusStopId, Integer> firstSequenceOf;

    public RaptorRoute(BusRouteId routeId,
                       RouteDirection direction,
                       List<BusStopId> stopIds,
                       List<RaptorStopTime> stopTimes,
                       List<RaptorTrip> trips) {
        if (routeId == null || direction == null) {
            throw new IllegalArgumentException("RaptorRoute routeId and direction required");
        }
        if (stopIds == null || stopIds.size() < 2) {
            throw new IllegalArgumentException(
                    "RaptorRoute requires at least 2 stops, got " + (stopIds == null ? 0 : stopIds.size()));
        }
        if (stopTimes == null || stopTimes.size() != stopIds.size()) {
            throw new IllegalArgumentException(
                    "RaptorRoute stopTimes size must equal stopIds size");
        }
        if (trips == null || trips.isEmpty()) {
            throw new IllegalArgumentException("RaptorRoute requires at least one trip");
        }

        this.routeId = routeId;
        this.direction = direction;
        this.stopIds = List.copyOf(stopIds);
        this.stopTimes = List.copyOf(stopTimes);
        this.trips = List.copyOf(trips);

        Map<BusStopId, Integer> firstSeq = new HashMap<>();
        for (int i = 0; i < this.stopIds.size(); i++) {
            firstSeq.putIfAbsent(this.stopIds.get(i), i);
        }
        this.firstSequenceOf = Collections.unmodifiableMap(firstSeq);
    }

    public BusRouteId routeId() {
        return routeId;
    }

    public RouteDirection direction() {
        return direction;
    }

    public int stopCount() {
        return stopIds.size();
    }

    public BusStopId stopAt(int sequenceIndex) {
        return stopIds.get(sequenceIndex);
    }

    public int lastSequenceIndex() {
        return stopIds.size() - 1;
    }

    public int firstSequenceOf(BusStopId stop) {
        Integer idx = firstSequenceOf.get(stop);
        return idx == null ? -1 : idx;
    }

    public int arrivalSecAt(RaptorTrip trip, int sequenceIndex) {
        return trip.startTimeSec() + stopTimes.get(sequenceIndex).arrivalOffsetSec();
    }

    public int departureSecAt(RaptorTrip trip, int sequenceIndex) {
        return trip.startTimeSec() + stopTimes.get(sequenceIndex).departureOffsetSec();
    }

    public RaptorTrip earliestTripAt(int sequenceIndex, int earliestBoardingSec) {
        int boardOffset = stopTimes.get(sequenceIndex).departureOffsetSec();
        int targetStartSec = earliestBoardingSec - boardOffset;
        int lo = 0;
        int hi = trips.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (trips.get(mid).startTimeSec() < targetStartSec) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo < trips.size() ? trips.get(lo) : null;
    }

    public List<BusStopId> stopIds() {
        return stopIds;
    }

    public List<RaptorTrip> trips() {
        return trips;
    }
}
