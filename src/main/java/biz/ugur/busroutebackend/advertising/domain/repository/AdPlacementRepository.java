package biz.ugur.busroutebackend.advertising.domain.repository;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

public interface AdPlacementRepository extends BaseRepository<AdPlacement, PlacementId> {

    Flux<AdPlacement> findByBusinessId(BusinessId businessId, Pageable pageable);

    Mono<Long> countByBusinessId(BusinessId businessId);

    Flux<AdPlacement> findByStatus(PlacementStatus status, Pageable pageable);

    Mono<Long> countByStatus(PlacementStatus status);

    Mono<AdPlacement> findByExternalRef(String externalServiceId, String externalRef);

    Flux<AdPlacement> findByExternalServiceId(String externalServiceId);

    Flux<AdPlacement> findByKind(PlacementKind kind, Pageable pageable);

    Mono<Long> countByKind(PlacementKind kind);

    Mono<Map<PlacementStatus, Long>> countsByStatus();

    Flux<AdPlacement> findActiveByTypeAt(PlacementType placementType, LocalDateTime moment);

    Flux<AdPlacement> findActiveByKindAndTargetType(PlacementKind kind, TargetType targetType);

    Flux<AdPlacement> findDueToActivate(LocalDateTime moment);

    Flux<AdPlacement> findDueToExpire(LocalDateTime moment);

    Flux<SalesReportRow> findForSalesReport(SalesReportFilter filter);

    Mono<Long> countForSalesReport(SalesReportFilter filter);

    Mono<SalesReportRowTotals> totalsForSalesReport(SalesReportFilter filter);
}
