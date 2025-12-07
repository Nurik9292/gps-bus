package biz.ugur.busroutebackend.banner.domain.exceptions;

import lombok.Getter;

@Getter
public class BannerNotFoundException extends BannerDomainException {

    private final String bannerId;

    public BannerNotFoundException(String bannerId) {
        super("NOT_FOUND", String.format("Banner not found with ID: %s", bannerId), Severity.WARNING);
        this.bannerId = bannerId;
    }

    public static BannerNotFoundException byId(String bannerId) {
        return new BannerNotFoundException(bannerId);
    }
}
