package biz.ugur.busroutebackend.advertising.application.usecase.client;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Public endpoint used by the mobile app to fetch ads relevant for a display context
 * (e.g. home banner, trip-search popup, push queue). Excludes map.html by design
 * (filtered in {@link AdPlacement#create}).
 */
@Service
public class GetActiveAdsUseCase extends BaseUseCase<GetActiveAdsUseCase.Query, List<AdPlacementResponse>> {

    public record Query(PlacementType placementType, String context) {}

    private final AdPlacementRepository placementRepository;
    private final AdPlacementResponseMapper responseMapper;

    public GetActiveAdsUseCase(AdPlacementRepository placementRepository,
                                AdPlacementResponseMapper responseMapper,
                                CorrelationContextService correlationService,
                                EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.responseMapper = responseMapper;
    }

    @Override
    protected Mono<List<AdPlacementResponse>> process(Query q) {
        Flux<AdPlacement> ads = placementRepository
                .findActiveByTypeAt(q.placementType(), LocalDateTime.now());

        if (q.context() != null && !q.context().isBlank()) {
            String needle = q.context().trim().toLowerCase();
            if ("map.html".equals(needle)) {
                return Mono.just(List.of());
            }
            ads = ads.filter(p -> {
                String contexts = p.getDisplayContexts();
                return contexts == null || contexts.isBlank()
                        || java.util.Arrays.stream(contexts.split(","))
                                .anyMatch(c -> c.trim().equalsIgnoreCase(needle));
            });
        }

        return ads
                .concatMap(responseMapper::toResponse)
                .collectList();
    }

    @Override
    protected String getBoundContext() { return "advertising.client"; }
}
