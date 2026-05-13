package biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring;

import java.time.Duration;
import java.time.Instant;

public record AlertEvent(
        String tenant,
        AlertKind kind,
        String details,
        Instant since,
        Duration downtime
) {
    public static AlertEvent degraded(String tenant, AlertKind kind, String details, Instant since) {
        return new AlertEvent(tenant, kind, details, since, null);
    }

    public static AlertEvent recovery(String tenant, String details, Instant since, Duration downtime) {
        return new AlertEvent(tenant, AlertKind.RECOVERY, details, since, downtime);
    }
}
