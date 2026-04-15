package biz.ugur.busroutebackend.business.application.usecase.admin;

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
public class DeleteBusinessUseCase extends BaseUseCase<String, Void> {

    private final BusinessRepository businessRepository;

    public DeleteBusinessUseCase(BusinessRepository businessRepository,
                                  CorrelationContextService correlationService,
                                  EventBus eventBus) {
        super(correlationService, eventBus);
        this.businessRepository = businessRepository;
    }

    @Override
    protected Mono<Void> process(String businessId) {
        BusinessId id = BusinessId.of(businessId);
        return businessRepository.existsById(id)
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? businessRepository.deleteById(id)
                                .doOnSuccess(v -> log.info("Business deleted: id={}", businessId))
                        : Mono.error(new BusinessNotFoundException(businessId)));
    }

    @Override
    protected String getBoundContext() { return "business.admin"; }
}
