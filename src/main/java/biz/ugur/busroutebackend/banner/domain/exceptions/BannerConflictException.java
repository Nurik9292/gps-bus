package biz.ugur.busroutebackend.banner.domain.exceptions;

import lombok.Getter;

@Getter
public class BannerConflictException extends BannerDomainException {

    private final String bannerId;
    private final String conflictReason;

    public BannerConflictException(String message) {
        super("CONFLICT", message, Severity.WARNING);
        this.bannerId = null;
        this.conflictReason = message;
    }

    public BannerConflictException(String bannerId, String conflictReason) {
        super("CONFLICT", String.format("Banner conflict for ID %s: %s", bannerId, conflictReason), Severity.WARNING);
        this.bannerId = bannerId;
        this.conflictReason = conflictReason;
    }

    public static BannerConflictException alreadyExists(String bannerId) {
        return new BannerConflictException(bannerId, "Banner already exists");
    }

    public static BannerConflictException overlappingPeriod(String bannerId) {
        return new BannerConflictException(bannerId, "Banner period overlaps with existing banner");
    }
}
