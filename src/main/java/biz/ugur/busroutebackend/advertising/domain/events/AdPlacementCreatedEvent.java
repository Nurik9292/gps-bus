package biz.ugur.busroutebackend.advertising.domain.events;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import lombok.Getter;

@Getter
public class AdPlacementCreatedEvent extends AdvertisingDomainEvent {

    private final String businessId;
    private final String tariffId;
    private final PlacementType placementType;
    private final PlacementKind kind;

    public AdPlacementCreatedEvent(String placementId,
                                    String businessId,
                                    String tariffId,
                                    PlacementType placementType,
                                    PlacementKind kind) {
        super(placementId);
        this.businessId = businessId;
        this.tariffId = tariffId;
        this.placementType = placementType;
        this.kind = kind != null ? kind : PlacementKind.COMMERCIAL;
    }

    @Override protected int getCurrentVersion() { return 2; }
}
