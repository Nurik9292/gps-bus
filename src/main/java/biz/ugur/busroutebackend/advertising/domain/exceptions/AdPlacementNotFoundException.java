package biz.ugur.busroutebackend.advertising.domain.exceptions;

public class AdPlacementNotFoundException extends AdvertisingDomainException {

    public AdPlacementNotFoundException(String placementId) {
        super("PLACEMENT_NOT_FOUND", "Ad placement not found: " + placementId, Severity.WARNING);
    }
}
