package biz.ugur.busroutebackend.banner.domain.exceptions;


import lombok.Getter;

@Getter
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

}
