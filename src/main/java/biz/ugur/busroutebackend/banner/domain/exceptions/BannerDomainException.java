package biz.ugur.busroutebackend.banner.domain.exceptions;

/**
 * Base exception for all Banner domain-related errors.
 * This exception serves as the root of the Banner bounded context exception hierarchy.
 *
 * All domain-specific exceptions should extend this class to maintain
 * a clear separation between domain errors and technical/infrastructure errors.
 */
public class BannerDomainException extends RuntimeException {

    public BannerDomainException(String message) {
        super(message);
    }

    public BannerDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
