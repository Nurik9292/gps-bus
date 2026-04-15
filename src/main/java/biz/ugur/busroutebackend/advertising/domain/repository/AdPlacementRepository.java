package biz.ugur.busroutebackend.advertising.domain.repository;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface AdPlacementRepository extends BaseRepository<AdPlacement, PlacementId> {

    Flux<AdPlacement> findByBusinessId(BusinessId businessId, Pageable pageable);

    Mono<Long> countByBusinessId(BusinessId businessId);

    Flux<AdPlacement> findByStatus(PlacementStatus status, Pageable pageable);

    /**
     * Active placements of a given type whose display window contains the moment.
     * Used by public endpoints serving ads to the mobile app.
     */
    Flux<AdPlacement> findActiveByTypeAt(PlacementType placementType, LocalDateTime moment);

    /**
     * Placements due to start (SCHEDULED with startsAt ≤ moment).
     * Used by a scheduler job to flip SCHEDULED → ACTIVE.
     */
    Flux<AdPlacement> findDueToActivate(LocalDateTime moment);

    /**
     * Active placements with endsAt ≤ moment. Used to flip ACTIVE → EXPIRED.
     */
    Flux<AdPlacement> findDueToExpire(LocalDateTime moment);
}
