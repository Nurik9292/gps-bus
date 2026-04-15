package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdPlacementNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CancelAdPlacementUseCase extends BaseUseCase<String, AdPlacementResponse> {

    private final AdPlacementRepository placementRepository;
    private final AdPlacementResponseMapper responseMapper;

    public CancelAdPlacementUseCase(AdPlacementRepository placementRepository,
                                     AdPlacementResponseMapper responseMapper,
                                     CorrelationContextService correlationService,
                                     EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.responseMapper = responseMapper;
    }

    @Override
    protected Mono<AdPlacementResponse> process(String placementId) {
        return placementRepository.findById(PlacementId.of(placementId))
                .switchIfEmpty(Mono.error(new AdPlacementNotFoundException(placementId)))
                .map(p -> p.cancel())
                .flatMap(placementRepository::save)
                .flatMap(responseMapper::toResponse)
                .doOnSuccess(r -> log.info("AdPlacement cancelled: id={}", r.id()));
    }

    @Override
    protected String getBoundContext() { return "advertising.admin"; }
}
