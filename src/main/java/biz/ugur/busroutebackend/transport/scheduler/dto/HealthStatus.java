package biz.ugur.busroutebackend.transport.scheduler.dto;

import java.time.Instant;

public record HealthStatus(boolean healthy, Instant lastCheck) {
}
