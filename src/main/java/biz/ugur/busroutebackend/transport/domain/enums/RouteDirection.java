package biz.ugur.busroutebackend.transport.domain.enums;

import lombok.Getter;

@Getter
public enum RouteDirection {
    FORWARD(0),
    BACKWARD(1);

    private final int value;

    RouteDirection(int value) {
        this.value = value;
    }

    public static RouteDirection fromValue(int value) {
        return switch (value) {
            case 0 -> FORWARD;
            case 1 -> BACKWARD;
            default -> throw new IllegalArgumentException("Invalid direction value: " + value);
        };
    }

    public RouteDirection opposite() {
        return this == FORWARD ? BACKWARD : FORWARD;
    }
}