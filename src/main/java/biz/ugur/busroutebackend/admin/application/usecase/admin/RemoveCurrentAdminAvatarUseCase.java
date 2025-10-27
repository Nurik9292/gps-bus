package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.admin.infrastructure.storage.AvatarStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class RemoveCurrentAdminAvatarUseCase extends BaseUseCase<Mono<AdminId>, Admin> {

    private final AdminRepository adminRepository;
    private final AvatarStorageService avatarStorageService;

    protected RemoveCurrentAdminAvatarUseCase(CorrelationContextService correlationService,
                                              EventBus eventBus,
                                              AdminRepository adminRepository,
                                              AvatarStorageService avatarStorageService) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
        this.avatarStorageService = avatarStorageService;
    }


    @Override
    protected Mono<Admin> process(Mono<AdminId> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<Admin> processInternal(AdminId adminId) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    String idValue = adminId.getValue();
                    log.info("Removing avatar for admin  - CorrelationId: {} - AdminId {}", correlationId.value(), idValue);

                    return adminRepository.findById(adminId)
                            .switchIfEmpty(Mono.error(new AdminNotFoundException(idValue, "id", correlationId)))
                            .flatMap(admin -> {
                                String oldAvatar = admin.getAvatar();
                                // Admin is immutable - removeAvatar() returns a new instance
                                Admin updatedAdmin = admin.removeAvatar();
                               return avatarStorageService.delete(oldAvatar)
                                       .then(updateAvatarInDatabase(updatedAdmin));
                            })
                            .doOnSuccess(admin -> log.info("Avatar removed successfully for admin: {}", admin.getUsername()))
                            .doOnError(error -> log.error("Failed to remove avatar for admin: {}: {}",
                                    idValue, error.getMessage()));
                });
    }


    private Mono<Admin> updateAvatarInDatabase(Admin admin) {
        return adminRepository.updateAvatar(admin.getId(), null);

    }
}