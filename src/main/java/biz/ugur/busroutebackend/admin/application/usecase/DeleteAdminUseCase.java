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
public class DeleteAdminUseCase implements UseCase<Mono<DeleteAdminUseCase.Request>, Mono<Void>> {

    private final AdminRepository adminRepository;
    private final CorrelationContextService correlationService;

    public DeleteAdminUseCase(AdminRepository adminRepository, CorrelationContextService correlationService) {
        this.adminRepository = adminRepository;
        this.correlationService = correlationService;
    }

    @Override
    public Mono<Void> execute(Mono<Request> request) {
        return correlationService.executeWithCorrelation(
                request.flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<Void> executeWithCorrelation(Request request) {
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