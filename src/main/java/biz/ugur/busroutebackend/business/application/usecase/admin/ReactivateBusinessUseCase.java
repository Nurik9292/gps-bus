package biz.ugur.busroutebackend.business.application.usecase.admin;

import biz.ugur.busroutebackend.business.application.dto.BusinessResponse;
import biz.ugur.busroutebackend.business.application.mapper.BusinessResponseMapper;
import biz.ugur.busroutebackend.business.domain.exceptions.BusinessNotFoundException;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ReactivateBusinessUseCase extends BaseUseCase<String, BusinessResponse> {

    private final BusinessRepository businessRepository;
    private final BusinessResponseMapper businessResponseMapper;

    public ReactivateBusinessUseCase(BusinessRepository businessRepository,
                                      BusinessResponseMapper businessResponseMapper,
                                      CorrelationContextService correlationService,
                                      EventBus eventBus) {
        super(correlationService, eventBus);
        this.businessRepository = businessRepository;
        this.businessResponseMapper = businessResponseMapper;
    }

    @Override
    protected Mono<BusinessResponse> process(String businessId) {
        return businessRepository.findById(BusinessId.of(businessId))
                .switchIfEmpty(Mono.error(new BusinessNotFoundException(businessId)))
                .map(Business -> Business.reactivate())
                .flatMap(businessRepository::save)
                .flatMap(businessResponseMapper::toResponse)
                .doOnSuccess(r -> log.info("Business reactivated: id={}", r.id()));
    }

    @Override
    protected String getBoundContext() { return "business.admin"; }
}
