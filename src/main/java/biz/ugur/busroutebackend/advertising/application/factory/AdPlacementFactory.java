package biz.ugur.busroutebackend.advertising.application.factory;

import biz.ugur.busroutebackend.advertising.application.dto.CreateAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdTariffNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdTariffRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.exceptions.BusinessNotFoundException;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AdPlacementFactory {

    private final BusinessRepository businessRepository;
    private final AdTariffRepository adTariffRepository;

    public AdPlacementFactory(BusinessRepository businessRepository,
                               AdTariffRepository adTariffRepository) {
        this.businessRepository = businessRepository;
        this.adTariffRepository = adTariffRepository;
    }

    public Mono<AdPlacement> create(CreateAdPlacementCommand cmd) {
        BusinessId businessId = BusinessId.of(cmd.businessId());
        TariffId tariffId = TariffId.of(cmd.tariffId());
        PlacementType type = PlacementType.from(cmd.placementType());

        return businessRepository.existsById(businessId)
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.just(true)
                        : Mono.error(new BusinessNotFoundException(cmd.businessId())))
                .then(adTariffRepository.findById(tariffId)
                        .switchIfEmpty(Mono.error(new AdTariffNotFoundException(cmd.tariffId()))))
                .map(tariff -> {
                    if (tariff.getPlacementType() != type) {
                        throw new AdvertisingValidationException("placement_type",
                                "tariff type " + tariff.getPlacementType()
                                        + " does not match requested " + type);
                    }
                    return AdPlacement.create(
                            businessId, tariffId, type,
                            cmd.title(), cmd.content(), cmd.imageUrl(),
                            cmd.targetUrl(), cmd.ctaText(),
                            PlacementWindow.of(cmd.startsAt(), cmd.endsAt()),
                            cmd.displayContexts(),
                            cmd.displayOrder());
                });
    }
}
