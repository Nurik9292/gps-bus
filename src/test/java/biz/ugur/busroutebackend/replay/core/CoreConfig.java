package biz.ugur.busroutebackend.replay.core;

public record CoreConfig(
        double dtSec,
        double w0Meters,
        double kWindowPerSpeed,
        double sigmaMeasDefaultMeters,
        double accuracyRefMeters,
        double dSnapMeters,
        double dMaxMeters,
        double gammaGate,
        double qPos,
        double qVel,
        double pInitPos,
        double pInitVel,
        double rMaxRate,
        double rMaxBaseMeters,
        double weakZvWeight,
        int nPersist,
        int mReanchor,
        double tLostSec,
        double tMaxSec,
        double vTargetMs,
        double aDepMs2,
        double aMaxMs2,
        double vStopKmh,
        double vMoveKmh,
        int hStop,
        int hDep,
        int hDec,
        double dwellMinSec) {

    public static CoreConfig defaults() {
        return new CoreConfig(
                1.0,
                150.0, 1.5,
                15.0, 5.0,
                80.0, 120.0,
                3.0,
                0.5, 0.8,
                40.0 * 40.0, 5.0 * 5.0,
                0.5, 30.0,
                0.1,
                4, 2,
                15.0, 90.0,
                12.5, 1.0, 2.0,
                1.0, 5.0,
                2, 3, 3,
                10.0);
    }
}
