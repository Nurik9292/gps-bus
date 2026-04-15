package biz.ugur.busroutebackend.advertising.domain.repository;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.model.AdTariff;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import reactor.core.publisher.Flux;

public interface AdTariffRepository extends BaseRepository<AdTariff, TariffId> {

    Flux<AdTariff> findActive();

    Flux<AdTariff> findActiveByType(PlacementType placementType);
}
