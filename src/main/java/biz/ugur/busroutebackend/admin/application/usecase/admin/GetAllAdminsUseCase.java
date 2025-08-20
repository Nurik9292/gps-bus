package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminList;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetAllAdminsUseCase extends BaseUseCase<Mono<Void>, AdminList> {

    private final AdminRepository adminRepository;

    public GetAllAdminsUseCase(AdminRepository adminRepository,
                               CorrelationContextService correlationContextService,
                               EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.adminRepository = adminRepository;
    }

    @Override
    protected Mono<AdminList> process(Mono<Void> request) {
        return request.then(Mono.defer(this::processInternal));
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<AdminList> processInternal() {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Getting admins from correlationId {}", correlationId);

            return adminRepository.findAllAdmins()
                    .map(AdminResult::fromDomain)
                    .collectList()
                    .flatMap(admins -> adminRepository.countActiveAdmins()
                            .map(activeCount -> new AdminList(admins, activeCount)))
                    .doOnSuccess(response -> log.debug("Retrieved {} admins ({} active)",
                            response.getAdmins().size(), response.getActiveCount()));
        });
    }
}
