package biz.ugur.busroutebackend.prediction.shadow;

import biz.ugur.busroutebackend.prediction.core.RouteLine;
import biz.ugur.busroutebackend.prediction.core.StopAware;

import java.util.List;

@FunctionalInterface
public interface V31StopEventSink {

    V31StopEventSink NO_OP = (fix, leaderGeom, s, direction, tripId, events) -> {
    };

    void onTick(V31Fix fix, RouteLine leaderGeom, double s, int direction, long tripId,
                List<StopAware.StopEvent> events);
}
