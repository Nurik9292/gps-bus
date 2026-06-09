package biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute;

import java.time.Instant;

public record OffRouteRecord(Instant detectedAt, double distanceMeters, double latitude, double longitude) {
}
