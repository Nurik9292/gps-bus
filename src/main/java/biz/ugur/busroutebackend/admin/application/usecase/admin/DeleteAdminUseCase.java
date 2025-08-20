package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminDeleteException;
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
public class DeleteAdminUseCase extends BaseUseCase<Mono<DeleteAdminUseCase.Request>, Void> {

    private final AdminRepository adminRepository;

    public DeleteAdminUseCase(AdminRepository adminRepository,
                              CorrelationContextService correlationService,
                              EventBus eventBus) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
    }


    @Override
    protected Mono<Void> process(Mono<Request> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<Void> processInternal(Request request) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    String idValue = request.adminId;
                    log.info("Delete admin  - CorrelationId: {} - AdminId: {}", correlationId.value(), idValue);

                    return adminRepository.findById(AdminId.of(idValue))
                            .switchIfEmpty(Mono.error(new AdminNotFoundException(idValue, "id", correlationId)))
                            .flatMap(admin -> {
                                if (admin.getIsSuperAdmin()) {
                                    return adminRepository.countActiveAdmins()
                                            .flatMap(count -> {
                                                if (count <= 1) {
                                                    return Mono.error(new AdminDeleteException(
                                                            "NOT_DELETED",
                                                            "Cannot delete the last super admin",
                                                            idValue,
                                                            correlationId));
                                                }
                                                return adminRepository.deleteById(AdminId.of(idValue));
                                            });
                                } else {
                                    return adminRepository.deleteById(AdminId.of(idValue));
                                }
                            })
                            .doOnSuccess(v -> log.info("Admin deleted successfully: {}", idValue))
                            .doOnError(error -> log.error("Failed to delete admin: {}", idValue, error));

                });
    }

    public record Request(String adminId) {}
}