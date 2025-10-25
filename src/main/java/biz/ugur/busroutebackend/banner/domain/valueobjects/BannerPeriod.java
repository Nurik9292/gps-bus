package biz.ugur.busroutebackend.banner.domain.valueobjects;


import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class BannerPeriod extends ValueObject {

    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    private BannerPeriod(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }

        if (endTime != null && endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("Banner Period endTime cannot be before startTime");
        }

        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static BannerPeriod between(LocalDateTime startTime, LocalDateTime endTime) {
        return new BannerPeriod(startTime, endTime);
    }

    public static BannerPeriod startingNowUntil(LocalDateTime endTime) {
        return new BannerPeriod(LocalDateTime.now(), endTime);
    }

    public static BannerPeriod startingAt(LocalDateTime startTime) {
        return new BannerPeriod(startTime, null);
    }

    public static BannerPeriod startingNow() {
        return new BannerPeriod(LocalDateTime.now(), null);
    }

    public boolean isActive(LocalDateTime now) {
        if (now == null) {
            now = LocalDateTime.now();
        }
        boolean afterStart = !now.isBefore(startTime);
        boolean beforeEnd = endTime == null || !now.isAfter(endTime);
        return afterStart && beforeEnd;
    }
}
