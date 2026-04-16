package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementList;
import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class GetAdPlacementsPaginatedUseCase
        extends BaseUseCase<GetAdPlacementsPaginatedUseCase.Query, AdPlacementList> {

    public record Query(int page, int size, String status, String businessId) {}

    private final AdPlacementRepository placementRepository;
    private final AdPlacementResponseMapper responseMapper;

    public GetAdPlacementsPaginatedUseCase(AdPlacementRepository placementRepository,
                                            AdPlacementResponseMapper responseMapper,
                                            CorrelationContextService correlationService,
                                            EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.responseMapper = responseMapper;
    }

    @Override
    protected Mono<AdPlacementList> process(Query q) {
        var pageable = PageRequest.of(q.page() - 1, q.size(),
                Sort.by(Sort.Direction.DESC, "created_at"));

        Flux<AdPlacement> items;
        Mono<Long> total;

        if (q.businessId() != null && !q.businessId().isBlank()) {
            BusinessId businessId = BusinessId.of(q.businessId());
            items = placementRepository.findByBusinessId(businessId, pageable);
            total = placementRepository.countByBusinessId(businessId);
        } else if (q.status() != null && !q.status().isBlank()) {
            PlacementStatus status = PlacementStatus.valueOf(q.status().toUpperCase());
            items = placementRepository.findByStatus(status, pageable);
            total = placementRepository.count();
        } else {
            items = placementRepository.findAll(pageable);
            total = placementRepository.count();
        }

        return items
                .concatMap(responseMapper::toResponse)
                .collectList()
                .zipWith(total)
                .map(tuple -> {
                    long activeCount = tuple.getT1().stream()
                            .filter(p -> "ACTIVE".equals(p.status()))
                            .count();
                    return AdPlacementList.of(
                            tuple.getT1(),
                            activeCount,
                            q.page(), q.size(),
                            tuple.getT2());
                });
    }

    @Override
    protected String getBoundContext() { return "advertising.admin"; }
}
