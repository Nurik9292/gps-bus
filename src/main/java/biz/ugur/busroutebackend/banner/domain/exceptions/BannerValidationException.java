package biz.ugur.busroutebackend.banner.domain.exceptions;

/**
 * Exception thrown when Banner validation fails.
 * This includes validation errors for Banner aggregate and its value objects
 * such as BannerTitle, BannerImage, BannerId, etc.
 */
public class BannerValidationException extends BannerDomainException {

    public BannerValidationException(String message) {
        super(message);
    }

    public BannerValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
