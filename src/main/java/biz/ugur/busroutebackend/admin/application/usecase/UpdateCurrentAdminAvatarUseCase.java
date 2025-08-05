package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateCurrentAdminAvatarUseCase implements UseCase<UpdateCurrentAdminAvatarUseCase.Request, Mono<Admin>> {

    private final AdminRepository adminRepository;
    private final EventBus eventBus;

    public record Request(AdminId adminId, String avatar) {}

    @Override
    public Mono<Admin> execute(Request request) {
        log.info("Updating avatar for admin: {}", request.adminId().getValue());

        if (request.avatar() != null && request.avatar().length() > 2_000_000) {
            return Mono.error(new IllegalArgumentException("Avatar too large (max 2MB)"));
        }

        if (request.avatar() != null && !isValidAvatarFormat(request.avatar())) {
            return Mono.error(new IllegalArgumentException("Invalid avatar format (only JPEG, PNG, WebP allowed)"));
        }

        return adminRepository.findById(request.adminId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Admin not found")))
                .filter(Admin::getIsActive)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Account is disabled")))
                .flatMap(admin -> {
                    log.info("💾 Saving avatar to database...");
                    return adminRepository.updateAvatar(admin.getId(), request.avatar())
                            .doOnNext(savedAdmin -> {
                                savedAdmin.getUncommittedEvents().forEach(eventBus::publish);
                                savedAdmin.markEventsAsCommitted();
                            });
                })
                .doOnSuccess(admin -> log.info("Avatar updated successfully for admin: {}", admin.getUsername()))
                .doOnError(error -> log.error("Failed to update avatar for admin: {}: {}",
                        request.adminId().getValue(), error.getMessage()));
    }

    private boolean isValidAvatarFormat(String avatar) {
        if (avatar == null || avatar.isEmpty()) return true;

        return avatar.startsWith("data:image/jpeg;base64,") ||
                avatar.startsWith("data:image/png;base64,") ||
                avatar.startsWith("data:image/webp;base64,") ||
                avatar.startsWith("http://") ||
                avatar.startsWith("https://") ||
                avatar.startsWith("/");
    }
}