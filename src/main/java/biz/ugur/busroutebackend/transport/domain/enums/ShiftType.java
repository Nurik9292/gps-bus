package biz.ugur.busroutebackend.transport.domain.enums;

import lombok.Getter;

import java.time.LocalTime;

@Getter
public enum ShiftType {
    FIRST(LocalTime.of(7, 0), LocalTime.of(14, 0)),
    SECOND(LocalTime.of(14, 0), LocalTime.of(21, 0));

    private final LocalTime startTime;
    private final LocalTime endTime;

    ShiftType(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static ShiftType getCurrentShift() {
        LocalTime now = LocalTime.now();
        if (now.isBefore(FIRST.startTime)) {
            return SECOND; // Before 7:00 - still second shift from previous day
        } else if (now.isBefore(SECOND.startTime)) {
            return FIRST;
        } else {
            return SECOND;
        }
    }

    public static ShiftType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Shift type cannot be null or empty");
        }
        return switch (value.toUpperCase().trim()) {
            case "FIRST", "1" -> FIRST;
            case "SECOND", "2" -> SECOND;
            default -> throw new IllegalArgumentException("Invalid shift type: " + value);
        };
    }

    public boolean isActive() {
        return this == getCurrentShift();
    }
}
