package biz.ugur.busroutebackend.advertising.domain.events;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import lombok.Getter;

@Getter
public class AdPlacementStatusChangedEvent extends AdvertisingDomainEvent {

    private final PlacementStatus from;
    private final PlacementStatus to;

    public AdPlacementStatusChangedEvent(String placementId, PlacementStatus from, PlacementStatus to) {
        super(placementId);
        this.from = from;
        this.to = to;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
