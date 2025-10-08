package biz.ugur.busroutebackend.transport.scheduler.dto;

import java.time.Instant;

public record GpsUpdateStats(
        long updatedCount,
        long createdCount,
        long failedCount,
        long invalidCount,
        long conflictCount,
        long durationMs,
        Instant timestamp,
        boolean successful,
        String errorMessage) {

    public GpsUpdateStats(long updatedCount, long createdCount, long failedCount,
                          long invalidCount, long conflictCount, long durationMs,
                          Instant timestamp, boolean successful) {
        this(updatedCount, createdCount, failedCount, invalidCount, conflictCount,
                durationMs, timestamp, successful, null);
    }
}
