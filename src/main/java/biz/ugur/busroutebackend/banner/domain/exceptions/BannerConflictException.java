package biz.ugur.busroutebackend.banner.domain.exceptions;


public class BannerConflictException extends BannerDomainException {

    public BannerConflictException(String message) {
        super(message);
    }

    public BannerConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
