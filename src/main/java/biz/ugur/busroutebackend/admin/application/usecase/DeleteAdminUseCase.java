package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteAdminUseCase implements UseCase<String, Mono<Void>> {

    private final AdminRepository adminRepository;

    public DeleteAdminUseCase(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @Override
    public Mono<Void> execute(String adminId) {
        log.info("Deleting admin: {}", adminId);

        return adminRepository.findById(AdminId.of(adminId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Admin not found: " + adminId)))
                .flatMap(admin -> {
                    if (admin.getIsSuperAdmin()) {
                        return adminRepository.countActiveAdmins()
                                .flatMap(count -> {
                                    if (count <= 1) {
                                        return Mono.error(new IllegalStateException("Cannot delete the last super admin"));
                                    }
                                    return adminRepository.deleteById(AdminId.of(adminId));
                                });
                    } else {
                        return adminRepository.deleteById(AdminId.of(adminId));
                    }
                })
                .doOnSuccess(v -> log.info("Admin deleted successfully: {}", adminId))
                .doOnError(error -> log.error("Failed to delete admin: {}", adminId, error));
    }
}