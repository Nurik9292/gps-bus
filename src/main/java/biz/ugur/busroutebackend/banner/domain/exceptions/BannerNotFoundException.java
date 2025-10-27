package biz.ugur.busroutebackend.banner.domain.exceptions;

/**
 * Exception thrown when a Banner cannot be found in the repository.
 * This exception indicates that a requested Banner does not exist.
 */
public class BannerNotFoundException extends BannerDomainException {

    private final String bannerId;

    public BannerNotFoundException(String bannerId) {
        super(String.format("Banner not found with ID: %s", bannerId));
        this.bannerId = bannerId;
    }

    public BannerNotFoundException(String bannerId, Throwable cause) {
        super(String.format("Banner not found with ID: %s", bannerId), cause);
        this.bannerId = bannerId;
    }

    public String getBannerId() {
        return bannerId;
    }
}
