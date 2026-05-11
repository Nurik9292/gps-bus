package biz.ugur.busroutebackend.routing.domain.model.raptor;

public record RaptorTrip(TripId id, int startTimeSec) {

    public RaptorTrip {
        if (id == null) {
            throw new IllegalArgumentException("RaptorTrip id is required");
        }
        if (startTimeSec < 0 || startTimeSec >= 24 * 3600 * 2) {
            throw new IllegalArgumentException(
                    "RaptorTrip startTimeSec out of plausible day range: " + startTimeSec);
        }
    }
}
