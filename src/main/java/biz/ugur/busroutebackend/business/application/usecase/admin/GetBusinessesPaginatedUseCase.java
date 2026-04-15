package biz.ugur.busroutebackend.business.application.usecase.admin;

import biz.ugur.busroutebackend.business.application.dto.BusinessList;
import biz.ugur.busroutebackend.business.application.dto.BusinessPaginationQuery;
import biz.ugur.busroutebackend.business.application.dto.BusinessResponse;
import biz.ugur.busroutebackend.business.application.mapper.BusinessResponseMapper;
import biz.ugur.busroutebackend.business.domain.enums.BusinessStatus;
import biz.ugur.busroutebackend.business.domain.enums.BusinessType;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class GetBusinessesPaginatedUseCase extends BaseUseCase<BusinessPaginationQuery, BusinessList> {

    private final BusinessRepository businessRepository;
    private final BusinessResponseMapper businessResponseMapper;

    public GetBusinessesPaginatedUseCase(BusinessRepository businessRepository,
                                          BusinessResponseMapper businessResponseMapper,
                                          CorrelationContextService correlationService,
                                          EventBus eventBus) {
        super(correlationService, eventBus);
        this.businessRepository = businessRepository;
        this.businessResponseMapper = businessResponseMapper;
    }

    @Override
    protected Mono<BusinessList> process(BusinessPaginationQuery query) {
        Pageable pageable = buildPageable(query);

        Flux<?> items;
        Mono<Long> total;

        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            BusinessStatus status = BusinessStatus.valueOf(query.getStatus().toUpperCase());
            items = businessRepository.findByStatus(status, pageable);
            total = businessRepository.countByStatus(status);
        } else if (query.getType() != null && !query.getType().isBlank()) {
            BusinessType type = BusinessType.from(query.getType());
            items = businessRepository.findByType(type, pageable);
            total = businessRepository.count();
        } else {
            items = businessRepository.findAll(pageable);
            total = businessRepository.count();
        }

        Mono<java.util.List<BusinessResponse>> responses = items
                .cast(biz.ugur.busroutebackend.business.domain.model.Business.class)
                .concatMap(businessResponseMapper::toResponse)
                .collectList();

        Mono<Long> approvedCount = businessRepository.countByStatus(BusinessStatus.APPROVED);

        return Mono.zip(responses, total, approvedCount)
                .map(tuple -> BusinessList.of(
                        tuple.getT1(),
                        tuple.getT3(),
                        query.getPage(),
                        query.getSize(),
                        tuple.getT2()));
    }

    @Override
    protected String getBoundContext() { return "business.admin"; }

    private Pageable buildPageable(BusinessPaginationQuery q) {
        Sort sort = Sort.unsorted();
        if (q.getSortField() != null && !q.getSortField().isBlank()) {
            Sort.Direction dir = "asc".equalsIgnoreCase(q.getSortOrder())
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
            sort = Sort.by(dir, q.getSortField());
        } else {
            sort = Sort.by(Sort.Direction.DESC, "created_at");
        }
        return PageRequest.of(q.getPage() - 1, q.getSize(), sort);
    }
}
