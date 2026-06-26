package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdPlacementNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.repository.AdClickEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdImpressionEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.storage.AdPlacementStorage;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteAdPlacementUseCase extends BaseUseCase<String, Void> {

    private final AdPlacementRepository placementRepository;
    private final AdPlacementStorage storage;
    private final AdImpressionEventRepository impressionEventRepository;
    private final AdClickEventRepository clickEventRepository;

    public DeleteAdPlacementUseCase(AdPlacementRepository placementRepository,
                                    AdPlacementStorage storage,
                                    AdImpressionEventRepository impressionEventRepository,
                                    AdClickEventRepository clickEventRepository,
                                    CorrelationContextService correlationService,
                                    EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.storage = storage;
        this.impressionEventRepository = impressionEventRepository;
        this.clickEventRepository = clickEventRepository;
    }

    @Override
    protected Mono<Void> process(String placementId) {
        PlacementId id = PlacementId.of(placementId);
        return placementRepository.findById(id)
                .switchIfEmpty(Mono.error(new AdPlacementNotFoundException(placementId)))
                .flatMap(placement -> deleteCreative(placement.getImageUrl())
                        .then(impressionEventRepository.deleteByPlacementId(id))
                        .then(clickEventRepository.deleteByPlacementId(id))
                        .then(placementRepository.deleteById(id)))
                .doOnSuccess(v -> log.info("AdPlacement deleted: id={}", placementId))
                .doOnError(error -> log.error("Failed to delete AdPlacement: id={}", placementId, error));
    }

    private Mono<Void> deleteCreative(String imageUrl) {
        return Mono.justOrEmpty(imageUrl)
                .flatMap(storage::delete)
                .then();
    }

    @Override
    protected String getBoundContext() {
        return "advertising.admin";
    }
}
