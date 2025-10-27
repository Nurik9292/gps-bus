package biz.ugur.busroutebackend.banner.domain.exceptions;


public class BannerValidationException extends BannerDomainException {

    public BannerValidationException(String message) {
        super(message);
    }

    public BannerValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
