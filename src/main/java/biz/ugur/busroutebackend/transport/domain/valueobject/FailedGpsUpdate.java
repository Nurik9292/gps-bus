package biz.ugur.busroutebackend.transport.domain.valueobject;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record FailedGpsUpdate(
        String id,
        String deviceId,
        String licensePlate,
        Double latitude,
        Double longitude,
        Double speed,
        Double course,
        LocalDateTime fixTime,
        LocalDateTime failedAt,
        String failureReason,
        FailureType failureType,
        int retryCount,
        LocalDateTime nextRetryAt
) {

    public enum FailureType {
        CONSTRAINT_VIOLATION,
        CONNECTION_ERROR,
        OPTIMISTIC_LOCK_EXHAUSTED,
        TIMEOUT,
        VALIDATION_ERROR,
        UNKNOWN
    }

    public FailedGpsUpdate {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(failedAt, "failedAt must not be null");
        Objects.requireNonNull(failureReason, "failureReason must not be null");
        Objects.requireNonNull(failureType, "failureType must not be null");

        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must be non-negative");
        }
    }


    public static FailedGpsUpdate create(
            String deviceId,
            String licensePlate,
            Double latitude,
            Double longitude,
            Double speed,
            Double course,
            LocalDateTime fixTime,
            String failureReason,
            FailureType failureType
    ) {
        return new FailedGpsUpdate(
                UUID.randomUUID().toString(),
                deviceId,
                licensePlate,
                latitude,
                longitude,
                speed,
                course,
                fixTime,
                LocalDateTime.now(),
                failureReason,
                failureType,
                0,
                null
        );
    }


    public FailedGpsUpdate withRetry(LocalDateTime nextRetryTime) {
        return new FailedGpsUpdate(
                this.id,
                this.deviceId,
                this.licensePlate,
                this.latitude,
                this.longitude,
                this.speed,
                this.course,
                this.fixTime,
                this.failedAt,
                this.failureReason,
                this.failureType,
                this.retryCount + 1,
                nextRetryTime
        );
    }

    public boolean canRetry(int maxRetries) {
        return retryCount < maxRetries;
    }

    public boolean hasValidCoordinates() {
        return latitude != null && longitude != null;
    }

}
