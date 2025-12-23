package biz.ugur.busroutebackend.transport.domain.valueobject;

/**
 * Source of route assignment for a vehicle
 *
 * Priority order (highest to lowest):
 * 1. SHIFT_ASSIGNMENT - Planned shift-based assignment (highest priority)
 * 2. GPS_DETECTED - Automatic GPS-based route detection
 * 3. MANUAL - Manual assignment by dispatcher
 * 4. EXTERNAL_API - [DEPRECATED] Assignment from external Bus Info API
 * 5. UNKNOWN - Source not determined
 */
public enum RouteSource {

    /**
     * Route assigned via shift planning system
     * Confidence: 100%
     * Priority: Highest (1)
     */
    SHIFT_ASSIGNMENT,

    /**
     * Route automatically detected via GPS trajectory analysis
     * Confidence: Variable (based on algorithm confidence)
     * Priority: High (2)
     */
    GPS_DETECTED,

    /**
     * Route manually assigned by dispatcher
     * Confidence: 100%
     * Priority: Medium (3)
     */
    MANUAL,

    /**
     * Route assigned from external Bus Info API
     * @deprecated Will be removed after migration to autonomous system
     * Confidence: 100%
     * Priority: Low (4)
     */
    @Deprecated
    EXTERNAL_API,

    /**
     * Route source unknown or not yet determined
     * Confidence: 0%
     * Priority: Lowest (5)
     */
    UNKNOWN;

    /**
     * Get confidence level based on route source
     *
     * @return Confidence 0-100%
     */
    public int getDefaultConfidence() {
        return switch (this) {
            case SHIFT_ASSIGNMENT, MANUAL, EXTERNAL_API -> 100;
            case GPS_DETECTED -> 0; // Will be set by detection algorithm
            case UNKNOWN -> 0;
        };
    }

    /**
     * Check if this source should override another source
     *
     * @param other Other route source
     * @return true if this source has higher priority
     */
    public boolean hasHigherPriorityThan(RouteSource other) {
        return this.getPriority() < other.getPriority();
    }

    /**
     * Get priority level (lower number = higher priority)
     */
    private int getPriority() {
        return switch (this) {
            case SHIFT_ASSIGNMENT -> 1;
            case GPS_DETECTED -> 2;
            case MANUAL -> 3;
            case EXTERNAL_API -> 4;
            case UNKNOWN -> 5;
        };
    }

    /**
     * Check if this is an automatic source (not manual)
     */
    public boolean isAutomatic() {
        return this == SHIFT_ASSIGNMENT || this == GPS_DETECTED || this == EXTERNAL_API;
    }

    /**
     * Check if this source is reliable (has manual validation)
     */
    public boolean isReliable() {
        return this == SHIFT_ASSIGNMENT || this == MANUAL;
    }
}
