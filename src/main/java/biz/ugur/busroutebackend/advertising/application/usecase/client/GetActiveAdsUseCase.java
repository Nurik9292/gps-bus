package biz.ugur.busroutebackend.advertising.application.usecase.client;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GetActiveAdsUseCase extends BaseUseCase<GetActiveAdsUseCase.Query, List<AdPlacementResponse>> {

    public record Query(PlacementType placementType, String context) {}

    private final AdPlacementRepository placementRepository;
    private final AdPlacementTargetRepository targetRepository;
    private final AdPlacementResponseMapper responseMapper;

    public GetActiveAdsUseCase(AdPlacementRepository placementRepository,
                                AdPlacementTargetRepository targetRepository,
                                AdPlacementResponseMapper responseMapper,
                                CorrelationContextService correlationService,
                                EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.targetRepository = targetRepository;
        this.responseMapper = responseMapper;
    }

    @Override
    protected Mono<List<AdPlacementResponse>> process(Query q) {
        return placementRepository.findActiveByTypeAt(q.placementType(), LocalDateTime.now())
                .collectList()
                .flatMap(placements -> filterByContext(placements, q.context()))
                .flatMap(filtered -> responseMapper.toResponses(filtered).collectList());
    }

    private Mono<List<AdPlacement>> filterByContext(List<AdPlacement> placements, String context) {
        Optional<TargetType> targetType = parseContext(context);
        if (targetType.isEmpty() || placements.isEmpty()) {
            return Mono.just(placements);
        }
        List<PlacementId> ids = placements.stream().map(AdPlacement::getId).toList();
        return targetRepository.findByPlacementIds(ids)
                .map(map -> placements.stream()
                        .filter(p -> matchesTarget(map.get(p.getId().getValue()), targetType.get()))
                        .toList());
    }

    private boolean matchesTarget(List<PlacementTarget> targets, TargetType wanted) {
        if (targets == null) return false;
        return targets.stream().anyMatch(t -> t.getTargetType() == wanted);
    }

    private Optional<TargetType> parseContext(String context) {
        if (context == null || context.isBlank()) return Optional.empty();
        return switch (context.trim().toLowerCase()) {
            case "home"                  -> Optional.of(TargetType.HOME);
            case "popup"                 -> Optional.of(TargetType.POPUP);
            case "routes", "routes_list" -> Optional.of(TargetType.ROUTES_LIST);
            case "stops",  "stops_list"  -> Optional.of(TargetType.STOPS_LIST);
            case "places", "places_list" -> Optional.of(TargetType.PLACES_LIST);
            case "route"                 -> Optional.of(TargetType.ROUTE);
            case "stop"                  -> Optional.of(TargetType.STOP);
            case "place"                 -> Optional.of(TargetType.PLACE);
            default                      -> Optional.empty();
        };
    }

    @Override
    protected String getBoundContext() { return "advertising.client"; }
}
