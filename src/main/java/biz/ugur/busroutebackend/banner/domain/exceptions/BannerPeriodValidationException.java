package biz.ugur.busroutebackend.banner.domain.exceptions;

/**
 * Exception thrown when BannerPeriod validation fails.
 * This is a specific type of validation exception for period-related errors
 * such as invalid date ranges, end time before start time, etc.
 */
public class BannerPeriodValidationException extends BannerValidationException {

    public BannerPeriodValidationException(String message) {
        super(message);
    }

    public BannerPeriodValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
