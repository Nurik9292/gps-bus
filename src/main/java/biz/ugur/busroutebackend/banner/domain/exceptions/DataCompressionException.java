package biz.ugur.busroutebackend.banner.domain.exceptions;


public class DataCompressionException extends BannerDomainException {

    public DataCompressionException(String message) {
        super(message);
    }

    public DataCompressionException(String message, Throwable cause) {
        super(message, cause);
    }
}
