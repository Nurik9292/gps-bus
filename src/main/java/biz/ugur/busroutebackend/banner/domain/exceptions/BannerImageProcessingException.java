package biz.ugur.busroutebackend.banner.domain.exceptions;

/**
 * Exception thrown when Banner image processing fails.
 * This is an infrastructure exception that wraps technical errors
 * from image processing operations such as resizing, compression, or format conversion.
 */
public class BannerImageProcessingException extends BannerDomainException {

    public BannerImageProcessingException(String message) {
        super(message);
    }

    public BannerImageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
