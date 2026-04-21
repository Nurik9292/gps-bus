package biz.ugur.busroutebackend.transport.infrastructure.prediction;

public enum GatekeeperDecision {
    ACCEPT,
    FORCE_ACCEPT_STALE,
    REJECT_OUTLIER,
    REJECT_TELEPORT_GAP,
    REJECT_OFF_ROUTE,
    PENDING_TELEPORT,
    COLD_START;

    public boolean allowsCoordinateWrite() {
        return this == ACCEPT || this == FORCE_ACCEPT_STALE;
    }
}
