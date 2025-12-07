package biz.ugur.busroutebackend.banner.domain.exceptions;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class BannerPeriodValidationException extends BannerValidationException {

    private final LocalDateTime startDate;
    private final LocalDateTime endDate;

    public BannerPeriodValidationException(String message) {
        super(message);
        this.startDate = null;
        this.endDate = null;
    }

    public BannerPeriodValidationException(LocalDateTime startDate, LocalDateTime endDate, String message) {
        super("period", message);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static BannerPeriodValidationException startAfterEnd(LocalDateTime startDate, LocalDateTime endDate) {
        return new BannerPeriodValidationException(startDate, endDate,
            "Start date must be before end date");
    }

    public static BannerPeriodValidationException startInPast(LocalDateTime startDate) {
        return new BannerPeriodValidationException(startDate, null,
            "Start date cannot be in the past");
    }

    public static BannerPeriodValidationException periodTooLong(LocalDateTime startDate, LocalDateTime endDate, int maxDays) {
        return new BannerPeriodValidationException(startDate, endDate,
            String.format("Banner period cannot exceed %d days", maxDays));
    }
}
