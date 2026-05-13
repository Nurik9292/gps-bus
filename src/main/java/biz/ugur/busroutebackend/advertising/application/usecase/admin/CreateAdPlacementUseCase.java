package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.dto.CreateAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.factory.AdPlacementFactory;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateAdPlacementUseCase
        extends BaseUseCase<Mono<CreateAdPlacementCommand>, AdPlacementResponse> {

    private final AdPlacementRepository placementRepository;
    private final AdPlacementTargetRepository targetRepository;
    private final AdPlacementFactory placementFactory;
    private final AdPlacementResponseMapper responseMapper;

    public CreateAdPlacementUseCase(AdPlacementRepository placementRepository,
                                     AdPlacementTargetRepository targetRepository,
                                     AdPlacementFactory placementFactory,
                                     AdPlacementResponseMapper responseMapper,
                                     CorrelationContextService correlationService,
                                     EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.targetRepository = targetRepository;
        this.placementFactory = placementFactory;
        this.responseMapper = responseMapper;
    }

    @Override
    protected Mono<AdPlacementResponse> process(Mono<CreateAdPlacementCommand> request) {
        return request.flatMap(cmd -> placementFactory.create(cmd)
                .flatMap(placementRepository::save)
                .flatMap(this::persistTargets)
                .flatMap(responseMapper::toResponse)
                .doOnSuccess(r -> log.info("AdPlacement created: id={} business={}",
                        r.id(), r.businessId())));
    }

    private Mono<AdPlacement> persistTargets(AdPlacement placement) {
        return targetRepository.replaceAll(placement.getId(), placement.getTargets())
                .thenReturn(placement);
    }

    @Override
    protected String getBoundContext() { return "advertising.admin"; }
}
