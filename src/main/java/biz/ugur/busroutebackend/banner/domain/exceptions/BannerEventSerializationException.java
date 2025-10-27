package biz.ugur.busroutebackend.banner.domain.exceptions;

/**
 * Exception thrown when Banner domain event serialization/deserialization fails.
 * This is an infrastructure exception that occurs during event store operations.
 */
public class BannerEventSerializationException extends BannerDomainException {

    public BannerEventSerializationException(String message) {
        super(message);
    }

    public BannerEventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
