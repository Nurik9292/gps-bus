package biz.ugur.busroutebackend.banner.domain.exceptions;


public class BannerDomainException extends RuntimeException {

    public BannerDomainException(String message) {
        super(message);
    }

    public BannerDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
