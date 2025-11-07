package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.application.dto.admin.UpdateCommand;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminAlreadyExistsException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.admin.infrastructure.storage.AvatarStorageService;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.storage.BaseImageStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UpdateAdminUseCase extends BaseUseCase<Mono<UpdateAdminUseCase.Request>, AdminResult> {

    private final AdminRepository adminRepository;
    private final AvatarStorageService avatarStorageService;

    public UpdateAdminUseCase(AdminRepository adminRepository,
                              AvatarStorageService avatarStorageService,
                              EventBus eventBus,
                              CorrelationContextService correlationService) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
        this.avatarStorageService = avatarStorageService;
    }


    @Override
    protected Mono<AdminResult> process(Mono<Request> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    public Mono<AdminResult> processInternal(Request request) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Updating admin - CorrelationId: {} - AdminId: {}", correlationId.value(), request.adminId());

            return adminRepository.findById(AdminId.of(request.adminId()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Admin not found - CorrelationId: {} - AdminId: {}", correlationId.value(), request.adminId());

                    return Mono.error(new AdminNotFoundException(request.adminId(), "id", correlationId));
                }))
                    .flatMap(existingAdmin ->
                        adminRepository.existsByUsername(request.command.username())
                        .flatMap(exists -> {
                            if (exists && !existingAdmin.getUsername().equals(request.command.username())) {
                                log.warn("Username conflict - CorrelationId: {} - Username: {}",
                                    correlationId.value(), request.command.username());

                                return Mono.error(new AdminAlreadyExistsException(request.command.username(), correlationId));
                            }

                            return applyUpdates(existingAdmin, request.command(), request.adminId())
                                .flatMap(adminRepository::save)
                                .map(AdminResult::fromDomain)
                                .doOnSuccess(result ->
                                    log.info("Admin updated successfully - CorrelationId: {} - Username: {} - Avatar: {}",
                                        correlationId.value(), result.username(), result.avatar()));
                                })
                    )
                    .doOnError(error -> log.error("Failed to update admin - CorrelationId: {} - AdminId: {}",
                                    correlationId.value(), request.adminId(), error));
        });
    }

    private Mono<Admin> applyUpdates(Admin admin, UpdateCommand command, String adminId) {
        // Apply basic updates
        Admin updatedAdmin = admin.updateProfile(command.username(), command.fullName());

        if (command.newPassword() != null && !command.newPassword().trim().isEmpty()) {
            updatedAdmin = updatedAdmin.changePassword(command.newPassword());
        }

        if (command.isActive() != null) {
            if (command.isActive())
                updatedAdmin = updatedAdmin.activate();
            else
                updatedAdmin = updatedAdmin.deactivate();
        }

        // Process avatar if provided
        if (command.avatar() != null) {
            String oldAvatarPath = admin.getAvatar();
            Admin finalAdmin = updatedAdmin;

            log.info("Processing avatar update for admin: {} - Old avatar: {} - New avatar provided: {}",
                adminId, oldAvatarPath, command.avatar().substring(0, Math.min(50, command.avatar().length())));

            return processAvatar(command.avatar(), adminId)
                .flatMap(newAvatarPath -> {
                    log.info("New avatar saved: {} - Deleting old avatar: {}", newAvatarPath, oldAvatarPath);

                    // Delete old avatar if exists and is different from new one
                    return deleteOldAvatar(oldAvatarPath, newAvatarPath)
                        .then(Mono.just(finalAdmin.updateAvatar(newAvatarPath)))
                        .doOnSuccess(a -> log.info("Avatar updated successfully for admin: {}", adminId));
                });
        }

        return Mono.just(updatedAdmin);
    }

    private Mono<String> processAvatar(String avatar, String adminId) {
        if (avatar == null || avatar.trim().isEmpty()) {
            return Mono.empty();
        }

        // If it's a base64 image, save it to storage
        if (avatar.startsWith("data:image/")) {
            return avatarStorageService.save(adminId, avatar)
                .map(BaseImageStorageService.Result::originalPath)
                .doOnSuccess(path -> log.info("Avatar saved for admin {}: {}", adminId, path))
                .onErrorResume(error -> {
                    log.error("Failed to save avatar for admin {}: {}", adminId, error.getMessage());
                    return Mono.empty();
                });
        }

        // If it's already a URL, just return it
        return Mono.just(avatar);
    }

    private Mono<Void> deleteOldAvatar(String oldAvatarPath, String newAvatarPath) {
        // Don't delete if old avatar doesn't exist or is same as new one
        if (oldAvatarPath == null || oldAvatarPath.isBlank() ||
            oldAvatarPath.equals(newAvatarPath) || oldAvatarPath.startsWith("data:")) {
            return Mono.empty();
        }

        log.info("Deleting old avatar: {}", oldAvatarPath);

        return avatarStorageService.delete(oldAvatarPath)
            .doOnSuccess(v -> log.info("Old avatar deleted successfully: {}", oldAvatarPath))
            .onErrorResume(error -> {
                // Don't fail the update if avatar deletion fails
                log.warn("Failed to delete old avatar {}: {}", oldAvatarPath, error.getMessage());
                return Mono.empty();
            });
    }


    public record Request(String adminId, UpdateCommand command) {}
}


