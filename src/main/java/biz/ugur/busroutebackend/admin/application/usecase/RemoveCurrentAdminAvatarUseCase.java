package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.shared.infrastructure.storage.AvatarStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoveCurrentAdminAvatarUseCase implements UseCase<Mono<AdminId>, Mono<Admin>> {

    private final AdminRepository adminRepository;
    private final EventBus eventBus;
    private final CorrelationContextService correlationService;
    private final AvatarStorageService avatarStorageService;

    @Override
    public Mono<Admin> execute(Mono<AdminId> adminId) {
        log.info("🚀 USECASE STEP 1: Execute called");
        return correlationService
                .executeWithCorrelation(adminId.flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<Admin> executeWithCorrelation(AdminId adminId) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    String idValue = adminId.getValue();
                    log.info("Removing avatar for admin  - CorrelationId: {} - AdminId {}", correlationId.value(), idValue);

                    return adminRepository.findById(adminId)
                            .switchIfEmpty(Mono.error(new AdminNotFoundException(idValue, "id", correlationId)))
                            .flatMap(admin -> {
                                String oldAvatar = admin.getAvatar();
                                System.out.println("Removing avatar for admin  - OldAvatar: " + oldAvatar);
                                admin.removeAvatar();
                               return avatarStorageService.deleteAvatar(oldAvatar)
                                       .then(updateAvatarInDatabase(admin));

                            })
                            .doOnSuccess(admin -> log.info("Avatar removed successfully for admin: {}", admin.getUsername()))
                            .doOnError(error -> log.error("Failed to remove avatar for admin: {}: {}",
                                    idValue, error.getMessage()));
                });
    }


    private Mono<Admin> updateAvatarInDatabase(Admin admin) {
        return adminRepository.updateAvatar(admin.getId(), null)
                .doOnNext(savedAdmin -> {
                    savedAdmin.getUncommittedEvents().forEach(eventBus::publish);
                    savedAdmin.markEventsAsCommitted();
                });
    }
}