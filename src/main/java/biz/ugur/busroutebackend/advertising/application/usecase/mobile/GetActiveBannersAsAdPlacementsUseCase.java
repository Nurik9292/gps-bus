package biz.ugur.busroutebackend.advertising.application.usecase.mobile;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.dto.BannerResponse;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.mapper.BannerJsonMapper;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GetActiveBannersAsAdPlacementsUseCase
        extends BaseUseCase<TargetType, List<BannerResponse>> {

    private final AdPlacementRepository placementRepository;

    public GetActiveBannersAsAdPlacementsUseCase(AdPlacementRepository placementRepository,
                                                 CorrelationContextService correlationService,
                                                 EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
    }

    @Override
    protected Mono<List<BannerResponse>> process(TargetType targetType) {
        return placementRepository
                .findActiveByKindAndTargetType(PlacementKind.EDITORIAL, targetType)
                .map(BannerJsonMapper::fromAdPlacement)
                .collectList();
    }

    @Override
    protected String getBoundContext() {
        return "advertising.mobile";
    }
}
