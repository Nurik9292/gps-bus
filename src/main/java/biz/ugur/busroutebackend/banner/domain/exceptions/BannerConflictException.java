package biz.ugur.busroutebackend.banner.domain.exceptions;

/**
 * Exception thrown when a Banner operation conflicts with existing state.
 * Examples include:
 * - Overlapping banner periods for the same type
 * - Duplicate display orders within the same period
 * - Concurrent modification conflicts
 */
public class BannerConflictException extends BannerDomainException {

    public BannerConflictException(String message) {
        super(message);
    }

    public BannerConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
