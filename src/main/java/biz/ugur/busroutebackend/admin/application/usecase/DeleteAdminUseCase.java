package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminDeleteException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminDomainException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteAdminUseCase implements UseCase<Mono<String>, Mono<Void>> {

    private final AdminRepository adminRepository;
    private final CorrelationContextService correlationService;

    public DeleteAdminUseCase(AdminRepository adminRepository, CorrelationContextService correlationService) {
        this.adminRepository = adminRepository;
        this.correlationService = correlationService;
    }

    @Override
    public Mono<Void> execute(Mono<String> adminId) {
        return correlationService.executeWithCorrelation(
                adminId.flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<Void> executeWithCorrelation(String adminId) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.info("Delete admin  - CorrelationId: {} - AdminId: {}", correlationId.value(), adminId);

                    return adminRepository.findById(AdminId.of(adminId))
                            .switchIfEmpty(Mono.error(new AdminNotFoundException(adminId, "id", correlationId)))
                            .flatMap(admin -> {
                                if (admin.getIsSuperAdmin()) {
                                    return adminRepository.countActiveAdmins()
                                            .flatMap(count -> {
                                                if (count <= 1) {
                                                    return Mono.error(new AdminDeleteException(
                                                            "NOT_DELETED",
                                                            "Cannot delete the last super admin",
                                                            adminId,
                                                            correlationId));
                                                }
                                                return adminRepository.deleteById(AdminId.of(adminId));
                                            });
                                } else {
                                    return adminRepository.deleteById(AdminId.of(adminId));
                                }
                            })
                            .doOnSuccess(v -> log.info("Admin deleted successfully: {}", adminId))
                            .doOnError(error -> log.error("Failed to delete admin: {}", adminId, error));

                });
    }
}