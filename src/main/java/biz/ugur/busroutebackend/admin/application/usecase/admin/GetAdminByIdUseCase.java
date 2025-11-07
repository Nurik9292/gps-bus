package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetAdminByIdUseCase extends BaseUseCase<Mono<GetAdminByIdUseCase.Request>, AdminResult> {

    private final AdminRepository adminRepository;

    public GetAdminByIdUseCase(AdminRepository adminRepository,
                               CorrelationContextService correlationService,
                               EventBus eventBus) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
    }

    @Override
    protected Mono<AdminResult> process(Mono<Request> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<AdminResult> processInternal(Request request) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    String idValue = request.adminId;
                    log.info("Get admin by ID - CorrelationId: {} - AdminId: {}", correlationId.value(), idValue);

                    return adminRepository.findById(AdminId.of(idValue))
                            .switchIfEmpty(Mono.error(new AdminNotFoundException(idValue, "id", correlationId)))
                            .map(AdminResult::fromDomain)
                            .doOnSuccess(result -> log.info("Admin found - CorrelationId: {} - Username: {}",
                                    correlationId.value(), result.username()))
                            .doOnError(error -> log.error("Failed to get admin - CorrelationId: {} - AdminId: {}",
                                    correlationId.value(), idValue, error));
                });
    }

    public record Request(String adminId) {}
}
