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
public class ApproveAdPlacementUseCase
        extends BaseUseCase<ApproveAdPlacementUseCase.Request, AdPlacementResponse> {

    public record Request(String placementId, String adminId) {}

    private final AdPlacementRepository placementRepository;
    private final AdPlacementResponseMapper responseMapper;

    public ApproveAdPlacementUseCase(AdPlacementRepository placementRepository,
                                      AdPlacementResponseMapper responseMapper,
                                      CorrelationContextService correlationService,
                                      EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.responseMapper = responseMapper;
    }

    @Override
    protected Mono<AdPlacementResponse> process(Request req) {
        return placementRepository.findById(PlacementId.of(req.placementId()))
                .switchIfEmpty(Mono.error(new AdPlacementNotFoundException(req.placementId())))
                .map(p -> p.approve(req.adminId()))
                .flatMap(placementRepository::save)
                .transform(this::persistAndPublish)
                .flatMap(responseMapper::toResponse)
                .doOnSuccess(r -> log.info("AdPlacement approved: id={} by adminId={}",
                        r.id(), req.adminId()));
    }

    @Override
    protected String getBoundContext() { return "advertising.admin"; }
}
