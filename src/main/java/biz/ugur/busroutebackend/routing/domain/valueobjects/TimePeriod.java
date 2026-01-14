package biz.ugur.busroutebackend.routing.domain.valueobjects;

import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDateTime;


@Getter
public enum TimePeriod {

    MORNING_RUSH(7, 9, 1.2, 6, 12.0),
    DAYTIME(10, 16, 1.1, 12, 18.0),
    EVENING_RUSH(17, 19, 1.4, 8, 12.0),
    EVENING(20, 22, 1.1, 15, 25.0),
    NIGHT(23, 6, 0.9, 25, 30.0);

    private final int startHour;
    private final int endHour;
    private final double trafficMultiplier;
    private final int baseWaitingMinutes;
    private final double averageSpeedKmh;

    TimePeriod(int startHour, int endHour, double trafficMultiplier,
               int baseWaitingMinutes, double averageSpeedKmh) {
        this.startHour = startHour;
        this.endHour = endHour;
        this.trafficMultiplier = trafficMultiplier;
        this.baseWaitingMinutes = baseWaitingMinutes;
        this.averageSpeedKmh = averageSpeedKmh;
    }

    public static TimePeriod fromHour(int hour) {
        if (hour >= 7 && hour <= 9) {
            return MORNING_RUSH;
        } else if (hour >= 10 && hour <= 16) {
            return DAYTIME;
        } else if (hour >= 17 && hour <= 19) {
            return EVENING_RUSH;
        } else if (hour >= 20 && hour <= 22) {
            return EVENING;
        } else {
            return NIGHT;
        }
    }

    public static TimePeriod fromDateTime(LocalDateTime dateTime) {
        return fromHour(dateTime.getHour());
    }

    public static boolean isWeekend(LocalDateTime dateTime) {
        DayOfWeek day = dateTime.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public boolean isRushHour() {
        return this == MORNING_RUSH || this == EVENING_RUSH;
    }

    public boolean isNight() {
        return this == NIGHT;
    }

    public double getTrafficMultiplier(boolean isWeekend) {
        return isWeekend ? trafficMultiplier * 0.8 : trafficMultiplier;
    }

    public int getBaseWaitingMinutes(boolean isWeekend) {
        int baseTime = baseWaitingMinutes;
        if (isWeekend) {
            baseTime += 5;
        }
        return Math.min(baseTime, 30);
    }

}
