package biz.ugur.busroutebackend.banner.domain.exceptions;


public class BannerImageProcessingException extends BannerDomainException {

    public BannerImageProcessingException(String message) {
        super(message);
    }

    public BannerImageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
