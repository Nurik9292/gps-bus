package biz.ugur.busroutebackend.replay;

import biz.ugur.busroutebackend.prediction.core.GpsFix;

import java.time.Instant;

public class InputValidator {

    public enum DropReason { FUTURE_TS, DUPLICATE_TS, NON_INCREASING_TS, FROZEN_COORDS }

    public record Decision(boolean accepted, DropReason reason) {

        static Decision accept() {
            return new Decision(true, null);
        }

        static Decision drop(DropReason reason) {
            return new Decision(false, reason);
        }
    }

    private final double futureSkewSec;
    private final double frozenMinSpeedKmh;

    private Instant lastAcceptedTs;
    private Double lastLat;
    private Double lastLon;

    public InputValidator(double futureSkewSec, double frozenMinSpeedKmh) {
        this.futureSkewSec = futureSkewSec;
        this.frozenMinSpeedKmh = frozenMinSpeedKmh;
    }

    public static InputValidator spec9Defaults() {
        return new InputValidator(30.0, 5.0);
    }

    public Decision validate(GpsFix fix) {
        Instant receivedAt = fix.wallClock() != null ? fix.wallClock() : fix.timestamp();
        if (fix.timestamp().isAfter(receivedAt.plusMillis((long) (futureSkewSec * 1000)))) {
            return Decision.drop(DropReason.FUTURE_TS);
        }
        if (lastAcceptedTs != null && fix.timestamp().equals(lastAcceptedTs)) {
            return Decision.drop(DropReason.DUPLICATE_TS);
        }
        if (lastAcceptedTs != null && fix.timestamp().isBefore(lastAcceptedTs)) {
            return Decision.drop(DropReason.NON_INCREASING_TS);
        }
        if (lastLat != null && lastLon != null
                && fix.latitude() == lastLat && fix.longitude() == lastLon
                && fix.speedKmh() >= frozenMinSpeedKmh) {
            lastAcceptedTs = fix.timestamp();
            return Decision.drop(DropReason.FROZEN_COORDS);
        }
        lastAcceptedTs = fix.timestamp();
        lastLat = fix.latitude();
        lastLon = fix.longitude();
        return Decision.accept();
    }
}
