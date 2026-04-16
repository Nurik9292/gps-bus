package biz.ugur.busroutebackend.advertising.domain.events;

import lombok.Getter;

@Getter
public class AdPlacementApprovedEvent extends AdvertisingDomainEvent {

    private final String adminId;

    public AdPlacementApprovedEvent(String placementId, String adminId) {
        super(placementId);
        this.adminId = adminId;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
