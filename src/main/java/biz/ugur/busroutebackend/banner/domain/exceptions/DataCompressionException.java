package biz.ugur.busroutebackend.banner.domain.exceptions;

/**
 * Exception thrown when data compression/decompression fails.
 * This is an infrastructure exception for technical errors during compression operations.
 */
public class DataCompressionException extends BannerDomainException {

    public DataCompressionException(String message) {
        super(message);
    }

    public DataCompressionException(String message, Throwable cause) {
        super(message, cause);
    }
}
