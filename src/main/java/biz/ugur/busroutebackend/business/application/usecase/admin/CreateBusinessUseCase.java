package biz.ugur.busroutebackend.business.application.usecase.admin;

import biz.ugur.busroutebackend.business.application.dto.BusinessResponse;
import biz.ugur.busroutebackend.business.application.dto.CreateBusinessCommand;
import biz.ugur.busroutebackend.business.application.factory.BusinessFactory;
import biz.ugur.busroutebackend.business.application.mapper.BusinessResponseMapper;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateBusinessUseCase extends BaseUseCase<Mono<CreateBusinessCommand>, BusinessResponse> {

    private final BusinessRepository businessRepository;
    private final BusinessFactory businessFactory;
    private final BusinessResponseMapper businessResponseMapper;

    public CreateBusinessUseCase(BusinessRepository businessRepository,
                                  BusinessFactory businessFactory,
                                  BusinessResponseMapper businessResponseMapper,
                                  CorrelationContextService correlationService,
                                  EventBus eventBus) {
        super(correlationService, eventBus);
        this.businessRepository = businessRepository;
        this.businessFactory = businessFactory;
        this.businessResponseMapper = businessResponseMapper;
    }

    @Override
    protected Mono<BusinessResponse> process(Mono<CreateBusinessCommand> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "business.admin";
    }

    private Mono<BusinessResponse> processInternal(CreateBusinessCommand cmd) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Creating business: correlationId={} name={}", correlationId, cmd.name());
            return businessFactory.create(cmd)
                    .flatMap(businessRepository::save)
                    .flatMap(businessResponseMapper::toResponse)
                    .doOnSuccess(r -> log.info("Business created: id={} name={}", r.id(), r.name()))
                    .doOnError(e -> log.warn("Business creation failed: {}", e.getMessage()));
        });
    }
}
