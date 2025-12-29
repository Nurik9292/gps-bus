package biz.ugur.busroutebackend.transport.domain.valueobject;


public enum RouteSource {

    SHIFT_ASSIGNMENT,

    GPS_DETECTED,

    MANUAL,

    UNKNOWN;

    public int getDefaultConfidence() {
        return switch (this) {
            case SHIFT_ASSIGNMENT, MANUAL -> 100;
            case GPS_DETECTED -> 0;
            case UNKNOWN -> 0;
        };
    }

    public boolean hasHigherPriorityThan(RouteSource other) {
        return this.getPriority() < other.getPriority();
    }


    private int getPriority() {
        return switch (this) {
            case SHIFT_ASSIGNMENT -> 1;
            case GPS_DETECTED -> 2;
            case MANUAL -> 3;
            case UNKNOWN -> 4;
        };
    }


    public boolean isAutomatic() {
        return this == SHIFT_ASSIGNMENT || this == GPS_DETECTED;
    }


    public boolean isReliable() {
        return this == SHIFT_ASSIGNMENT || this == MANUAL;
    }
}
