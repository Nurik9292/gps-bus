package biz.ugur.busroutebackend.advertising.application.usecase.client;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdPlacementNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RecordImpressionUseCase extends BaseUseCase<String, Void> {

    private final AdPlacementRepository placementRepository;

    public RecordImpressionUseCase(AdPlacementRepository placementRepository,
                                    CorrelationContextService correlationService,
                                    EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
    }

    @Override
    protected Mono<Void> process(String placementId) {
        return placementRepository.findById(PlacementId.of(placementId))
                .switchIfEmpty(Mono.error(new AdPlacementNotFoundException(placementId)))
                .map(p -> p.recordImpression())
                .flatMap(placementRepository::save)
                .then();
    }

    @Override
    protected String getBoundContext() { return "advertising.client"; }
}
