package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementStatusCounts;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GetAdPlacementStatusCountsUseCase extends BaseUseCase<Void, AdPlacementStatusCounts> {

    private final AdPlacementRepository placementRepository;

    public GetAdPlacementStatusCountsUseCase(AdPlacementRepository placementRepository,
                                              CorrelationContextService correlationService,
                                              EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
    }

    @Override
    protected Mono<AdPlacementStatusCounts> process(Void input) {
        return placementRepository.countsByStatus()
                .map(AdPlacementStatusCounts::from);
    }

    @Override
    protected String getBoundContext() { return "advertising.admin"; }
}
