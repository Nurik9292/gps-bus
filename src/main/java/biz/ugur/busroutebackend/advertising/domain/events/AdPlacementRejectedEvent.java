package biz.ugur.busroutebackend.advertising.domain.events;

import lombok.Getter;

@Getter
public class AdPlacementRejectedEvent extends AdvertisingDomainEvent {

    private final String adminId;
    private final String reason;

    public AdPlacementRejectedEvent(String placementId, String adminId, String reason) {
        super(placementId);
        this.adminId = adminId;
        this.reason = reason;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
