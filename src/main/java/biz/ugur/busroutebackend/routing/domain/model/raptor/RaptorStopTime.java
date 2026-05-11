package biz.ugur.busroutebackend.routing.domain.model.raptor;

public record RaptorStopTime(int arrivalOffsetSec, int departureOffsetSec) {

    public RaptorStopTime {
        if (arrivalOffsetSec < 0) {
            throw new IllegalArgumentException(
                    "RaptorStopTime arrivalOffsetSec must be >= 0, got " + arrivalOffsetSec);
        }
        if (departureOffsetSec < arrivalOffsetSec) {
            throw new IllegalArgumentException(
                    "RaptorStopTime departureOffsetSec (" + departureOffsetSec
                            + ") must be >= arrivalOffsetSec (" + arrivalOffsetSec + ")");
        }
    }

    public static RaptorStopTime instant(int offsetSec) {
        return new RaptorStopTime(offsetSec, offsetSec);
    }
}
