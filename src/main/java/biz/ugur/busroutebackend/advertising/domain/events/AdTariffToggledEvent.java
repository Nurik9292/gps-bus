package biz.ugur.busroutebackend.advertising.domain.events;

import lombok.Getter;

@Getter
public class AdTariffToggledEvent extends AdvertisingDomainEvent {

    private final boolean active;

    public AdTariffToggledEvent(String tariffId, boolean active) {
        super(tariffId);
        this.active = active;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
