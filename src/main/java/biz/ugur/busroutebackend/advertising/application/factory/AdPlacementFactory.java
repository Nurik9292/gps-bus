package biz.ugur.busroutebackend.advertising.application.factory;

import biz.ugur.busroutebackend.advertising.application.dto.CreateAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.dto.PlacementTargetSpec;
import biz.ugur.busroutebackend.advertising.application.processor.AdPlacementImageProcessor;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdTariffNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdTariffRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;

import java.util.List;
import biz.ugur.busroutebackend.business.domain.exceptions.BusinessNotFoundException;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AdPlacementFactory {

    private final BusinessRepository businessRepository;
    private final AdTariffRepository adTariffRepository;
    private final AdPlacementImageProcessor imageProcessor;

    public AdPlacementFactory(BusinessRepository businessRepository,
                               AdTariffRepository adTariffRepository,
                               AdPlacementImageProcessor imageProcessor) {
        this.businessRepository = businessRepository;
        this.adTariffRepository = adTariffRepository;
        this.imageProcessor = imageProcessor;
    }

    public Mono<AdPlacement> create(CreateAdPlacementCommand cmd) {
        BusinessId businessId = BusinessId.of(cmd.businessId());
        TariffId tariffId = TariffId.of(cmd.tariffId());
        PlacementType type = PlacementType.from(cmd.placementType());
        PlacementKind kind = PlacementKind.from(cmd.kind());
        List<PlacementTarget> targets = resolveTargets(cmd.targets());

        return businessRepository.existsById(businessId)
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.just(true)
                        : Mono.error(new BusinessNotFoundException(cmd.businessId())))
                .then(adTariffRepository.findById(tariffId)
                        .switchIfEmpty(Mono.error(new AdTariffNotFoundException(cmd.tariffId()))))
                .flatMap(tariff -> {
                    if (tariff.getPlacementType() != type) {
                        return Mono.error(new AdvertisingValidationException("placement_type",
                                "tariff type " + tariff.getPlacementType()
                                        + " does not match requested " + type));
                    }
                    return imageProcessor.process(cmd.imageUrl())
                            .defaultIfEmpty("")
                            .map(storedImageUrl -> AdPlacement.create(
                                    businessId, tariffId, type,
                                    kind,
                                    cmd.title(), cmd.content(),
                                    storedImageUrl.isEmpty() ? null : storedImageUrl,
                                    cmd.targetUrl(), cmd.ctaText(),
                                    PlacementWindow.of(cmd.startsAt(), cmd.endsAt()),
                                    targets,
                                    cmd.displayOrder()));
                });
    }

    private static List<PlacementTarget> resolveTargets(List<PlacementTargetSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return List.of();
        }
        return specs.stream()
                .map(AdPlacementFactory::toDomainTarget)
                .toList();
    }

    private static PlacementTarget toDomainTarget(PlacementTargetSpec spec) {
        if (spec == null) {
            throw new AdvertisingValidationException("targets", "must not contain null entries");
        }
        TargetType type = TargetType.from(spec.targetType());
        return PlacementTarget.of(type, spec.targetId());
    }
}
