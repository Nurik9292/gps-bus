package biz.ugur.busroutebackend.prediction.shadow;

import biz.ugur.busroutebackend.prediction.core.StopAware;

import java.util.List;

@FunctionalInterface
public interface V31StopEventSink {

    V31StopEventSink NO_OP = (fix, direction, tripId, events) -> {
    };

    void accept(V31Fix fix, int direction, long tripId, List<StopAware.StopEvent> events);
}
