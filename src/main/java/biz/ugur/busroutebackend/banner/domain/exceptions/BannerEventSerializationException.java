package biz.ugur.busroutebackend.banner.domain.exceptions;


public class BannerEventSerializationException extends BannerDomainException {

    public BannerEventSerializationException(String message) {
        super(message);
    }

    public BannerEventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
