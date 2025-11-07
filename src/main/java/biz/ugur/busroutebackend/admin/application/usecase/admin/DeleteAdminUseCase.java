package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminDeleteException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.admin.infrastructure.storage.AvatarStorageService;
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
    private final AvatarStorageService avatarStorageService;

    public DeleteAdminUseCase(AdminRepository adminRepository,
                              AvatarStorageService avatarStorageService,
                              CorrelationContextService correlationService,
                              EventBus eventBus) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
        this.avatarStorageService = avatarStorageService;
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
                                // Store avatar path before deletion
                                String avatarPath = admin.getAvatar();

                                Mono<Void> deleteMono;
                                if (admin.getIsSuperAdmin()) {
                                    deleteMono = adminRepository.countActiveAdmins()
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
                                    deleteMono = adminRepository.deleteById(AdminId.of(idValue));
                                }

                                // Delete admin from DB, then delete avatar from storage
                                return deleteMono
                                        .then(Mono.defer(() -> {
                                            if (avatarPath != null && !avatarPath.isBlank()) {
                                                log.info("Deleting avatar for admin {}: {}", idValue, avatarPath);
                                                return avatarStorageService.delete(avatarPath)
                                                        .doOnSuccess(v -> log.info("Avatar deleted successfully for admin: {}", idValue))
                                                        .onErrorResume(error -> {
                                                            // Don't fail admin deletion if avatar deletion fails
                                                            log.warn("Failed to delete avatar for admin {}: {}", idValue, error.getMessage());
                                                            return Mono.empty();
                                                        });
                                            }
                                            return Mono.empty();
                                        }));
                            })
                            .doOnSuccess(v -> log.info("Admin deleted successfully: {}", idValue))
                            .doOnError(error -> log.error("Failed to delete admin: {}", idValue, error));

                });
    }

    public record Request(String adminId) {}
}