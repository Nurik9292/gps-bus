package biz.ugur.busroutebackend.advertising.domain.events;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TariffPeriod;
import lombok.Getter;

@Getter
public class AdTariffCreatedEvent extends AdvertisingDomainEvent {

    private final String name;
    private final PlacementType placementType;
    private final TariffPeriod period;
    private final long priceAmount;
    private final String currency;

    public AdTariffCreatedEvent(String tariffId, String name, PlacementType placementType,
                                 TariffPeriod period, long priceAmount, String currency) {
        super(tariffId);
        this.name = name;
        this.placementType = placementType;
        this.period = period;
        this.priceAmount = priceAmount;
        this.currency = currency;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
