package biz.ugur.busroutebackend.advertising.domain.events;

import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
public class AdPlacementUpdatedEvent extends AdvertisingDomainEvent {

    private final String updatedBy;
    private final Map<String, Object> changes;

    public AdPlacementUpdatedEvent(String placementId,
                                    String updatedBy,
                                    Map<String, Object> changes) {
        super(placementId);
        this.updatedBy = updatedBy;
        this.changes = Collections.unmodifiableMap(new HashMap<>(changes));
    }

    @Override protected int getCurrentVersion() { return 1; }
}
