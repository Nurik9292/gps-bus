package biz.ugur.busroutebackend.transport.domain.enums;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Optional;

@Getter
public enum ShiftType {
    FIRST(LocalTime.of(5, 0), LocalTime.of(14, 0)),
    SECOND(LocalTime.of(14, 0), LocalTime.of(23, 0)),
    FULL_DAY(LocalTime.of(5, 0), LocalTime.of(23, 0));

    public static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    private final LocalTime startTime;
    private final LocalTime endTime;

    ShiftType(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static ShiftType getCurrentShift() {
        LocalTime now = ZonedDateTime.now(ASHGABAT_ZONE).toLocalTime();

        if (now.isBefore(FIRST.startTime)) {
            return SECOND;
        } else if (now.isBefore(SECOND.startTime)) {
            return FIRST;
        } else if (now.isBefore(SECOND.endTime)) {
            return SECOND;
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
            case "FULL_DAY", "FULLDAY", "FULL" -> FULL_DAY;
            default -> throw new IllegalArgumentException("Invalid shift type: " + value);
        };
    }

    public boolean isActiveAt(LocalTime time) {
        return !time.isBefore(startTime) && time.isBefore(endTime);
    }

    public boolean isActive() {
        LocalTime now = ZonedDateTime.now(ASHGABAT_ZONE).toLocalTime();
        return isActiveAt(now);
    }

    public static Optional<ShiftType> operationalShiftAt(Instant instant) {
        LocalTime ashgabatTime = LocalTime.ofInstant(instant, ASHGABAT_ZONE);
        return Arrays.stream(values())
                .filter(shift -> shift != FULL_DAY)
                .filter(shift -> shift.isActiveAt(ashgabatTime))
                .findFirst();
    }

    public static LocalDate operationalDateAt(Instant instant) {
        return LocalDate.ofInstant(instant, ASHGABAT_ZONE);
    }

    public Instant startInstantOn(LocalDate operationalDate) {
        return operationalDate.atTime(startTime).atZone(ASHGABAT_ZONE).toInstant();
    }

    public Instant endInstantOn(LocalDate operationalDate) {
        return operationalDate.atTime(endTime).atZone(ASHGABAT_ZONE).toInstant();
    }
}
