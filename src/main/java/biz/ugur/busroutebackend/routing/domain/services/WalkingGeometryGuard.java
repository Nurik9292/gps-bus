package biz.ugur.busroutebackend.routing.domain.services;

public final class WalkingGeometryGuard {

    private static final double MAX_DETOUR_RATIO = 2.0;
    private static final double MIN_DETOUR_EXCESS_METERS = 150.0;

    private WalkingGeometryGuard() {
    }

    public static boolean isImplausibleDetour(double straightLineMeters, int osrmDistanceMeters) {
        if (straightLineMeters <= 0) {
            return false;
        }
        double excessMeters = osrmDistanceMeters - straightLineMeters;
        return osrmDistanceMeters > MAX_DETOUR_RATIO * straightLineMeters
                && excessMeters > MIN_DETOUR_EXCESS_METERS;
    }
}
