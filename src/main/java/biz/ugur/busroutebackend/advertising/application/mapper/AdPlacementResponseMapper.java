package biz.ugur.busroutebackend.advertising.application.mapper;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class AdPlacementResponseMapper {

    private final AdPlacementTargetRepository targetRepository;

    public AdPlacementResponseMapper(AdPlacementTargetRepository targetRepository) {
        this.targetRepository = targetRepository;
    }

    public Mono<AdPlacementResponse> toResponse(AdPlacement placement) {
        if (placement.getTargets() != null && !placement.getTargets().isEmpty()) {
            return Mono.fromCallable(() -> AdPlacementResponse.fromDomain(placement));
        }
        return targetRepository.findByPlacementId(placement.getId())
                .collectList()
                .map(placement::withTargets)
                .map(AdPlacementResponse::fromDomain);
    }

    public Flux<AdPlacementResponse> toResponses(List<AdPlacement> placements) {
        if (placements == null || placements.isEmpty()) {
            return Flux.empty();
        }
        List<PlacementId> ids = placements.stream().map(AdPlacement::getId).toList();
        return targetRepository.findByPlacementIds(ids)
                .flatMapMany(targetMap -> Flux.fromIterable(placements)
                        .map(p -> {
                            AdPlacement enriched = p.withTargets(
                                    targetMap.getOrDefault(p.getId().getValue(), List.of()));
                            return AdPlacementResponse.fromDomain(enriched);
                        }));
    }
}
