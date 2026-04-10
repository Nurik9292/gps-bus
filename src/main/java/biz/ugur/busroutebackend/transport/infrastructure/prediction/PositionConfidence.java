package biz.ugur.busroutebackend.transport.infrastructure.prediction;

/**
 * Confidence level of a vehicle's predicted position.
 * Used by notification systems to decide whether to trust
 * the position data for push notification triggers.
 */
public enum PositionConfidence {
    /**
     * GPS data is fresh (< 3s old), vehicle is snapped to route.
     * Position is highly reliable. Safe to trigger notifications.
     */
    HIGH,

    /**
     * GPS data is moderately fresh (3-10s old) or vehicle is in dead-reckoning.
     * Position is reasonably accurate. Notifications OK with wider distance threshold.
     */
    MEDIUM,

    /**
     * GPS data is stale (10-30s old). Prediction may have drifted.
     * Use with caution for notifications — add extra buffer distance.
     */
    LOW,

    /**
     * GPS data is very stale (>30s) or absent.
     * Position is unreliable. Do NOT trigger notifications based on this.
     */
    STALE
}
