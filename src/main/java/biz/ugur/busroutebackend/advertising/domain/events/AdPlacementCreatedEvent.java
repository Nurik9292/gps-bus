package biz.ugur.busroutebackend.advertising.domain.events;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import lombok.Getter;

@Getter
public class AdPlacementCreatedEvent extends AdvertisingDomainEvent {

    private final String businessId;
    private final String tariffId;
    private final PlacementType placementType;

    public AdPlacementCreatedEvent(String placementId,
                                    String businessId,
                                    String tariffId,
                                    PlacementType placementType) {
        super(placementId);
        this.businessId = businessId;
        this.tariffId = tariffId;
        this.placementType = placementType;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
