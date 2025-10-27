package biz.ugur.busroutebackend.banner.domain.exceptions;


public class BannerPeriodValidationException extends BannerValidationException {

    public BannerPeriodValidationException(String message) {
        super(message);
    }

    public BannerPeriodValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
