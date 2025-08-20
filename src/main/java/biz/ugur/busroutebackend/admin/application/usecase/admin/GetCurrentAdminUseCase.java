package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class GetCurrentAdminUseCase extends BaseUseCase<Mono<GetCurrentAdminUseCase.Query>, Admin> {

    private final AdminRepository adminRepository;
    private final CorrelationContextService correlationService;

    public GetCurrentAdminUseCase(AdminRepository adminRepository,
                                  CorrelationContextService correlationService,
                                  EventBus eventBus) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
        this.correlationService = correlationService;
    }

    public record Query(AdminId adminId) {}


    @Override
    protected Mono<Admin> process(Mono<Query> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<Admin> processInternal(Query query) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    String adminId = query.adminId.getValue();
                    log.info("Getting current admin info for - CorrelationId: {} - AdminId: {}",
                            correlationId.value(), query.adminId());

                    return adminRepository.findById(query.adminId())
                            .switchIfEmpty(Mono.error(new AdminNotFoundException(adminId, "id", correlationId)))
                            .doOnSuccess(admin -> log.debug("Current admin info retrieved: {}", admin.getUsername()))
                            .doOnError(error -> log.warn("Failed to get current admin info for {}: {}",adminId, error.getMessage()));
                });
    }


}