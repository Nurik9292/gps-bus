package biz.ugur.busroutebackend.advertising.application.factory;

import biz.ugur.busroutebackend.advertising.application.dto.CreateAdTariffCommand;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TariffPeriod;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.model.AdTariff;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.Price;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffName;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AdTariffFactory {

    public Mono<AdTariff> create(CreateAdTariffCommand cmd) {
        return Mono.fromCallable(() -> {
            if (cmd.priceAmount() == null) {
                throw new AdvertisingValidationException("price_amount", "must not be null");
            }
            return AdTariff.create(
                    TariffName.of(cmd.name()),
                    cmd.description(),
                    PlacementType.from(cmd.placementType()),
                    TariffPeriod.from(cmd.period()),
                    Price.ofMinor(cmd.priceAmount(),
                            cmd.currency() != null ? cmd.currency() : "TMT"),
                    cmd.maxImpressions(),
                    cmd.maxClicks(),
                    cmd.dailyImpressionCap(),
                    cmd.displayOrder());
        });
    }
}
