package biz.ugur.busroutebackend.advertising.application.mapper;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AdPlacementResponseMapper {

    public Mono<AdPlacementResponse> toResponse(AdPlacement placement) {
        return Mono.fromCallable(() -> AdPlacementResponse.fromDomain(placement));
    }
}
