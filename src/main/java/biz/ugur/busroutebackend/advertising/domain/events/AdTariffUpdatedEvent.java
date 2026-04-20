package biz.ugur.busroutebackend.advertising.domain.events;

import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
public class AdTariffUpdatedEvent extends AdvertisingDomainEvent {

    private final Map<String, Object> changes;

    public AdTariffUpdatedEvent(String tariffId, Map<String, Object> changes) {
        super(tariffId);
        this.changes = Collections.unmodifiableMap(new HashMap<>(changes));
    }

    @Override protected int getCurrentVersion() { return 1; }
}
