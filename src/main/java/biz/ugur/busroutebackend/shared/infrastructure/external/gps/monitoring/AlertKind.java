package biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring;

public enum AlertKind {
    HTTP_ERROR,
    EMPTY,
    DROP,
    STALE,
    RECOVERY,
    VEHICLE_OFF_ROUTE,
    ASSIGNED_NOT_ON_LINE
}
