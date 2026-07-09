package biz.ugur.busroutebackend.prediction.core;

import java.time.Instant;
import java.util.List;

public interface StopAware {

    record Eta(String stopId, double etaSec, boolean reliable) {}

    enum StopEventType { DECEL_ENTER, DWELL_ENTER, DWELL_EXIT, SKIP, AT_TERMINAL, DWELL_OUTLIER }

    record StopEvent(StopEventType type, String stopId, Instant at) {}

    List<Eta> etas();

    List<StopEvent> drainEvents();
}
