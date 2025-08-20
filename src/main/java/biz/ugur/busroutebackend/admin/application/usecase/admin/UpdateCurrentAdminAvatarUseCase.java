package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.storage.AvatarStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class UpdateCurrentAdminAvatarUseCase extends BaseUseCase<Mono<UpdateCurrentAdminAvatarUseCase.Request>, Admin> {

    private final AdminRepository adminRepository;
    private final AvatarStorageService avatarStorageService;

    protected UpdateCurrentAdminAvatarUseCase(CorrelationContextService correlationService,
                                              EventBus eventBus,
                                              AdminRepository adminRepository,
                                              AvatarStorageService avatarStorageService) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
        this.avatarStorageService = avatarStorageService;
    }


    @Override
    protected Mono<Admin> process(Mono<Request> request) {
        return request.flatMap(this::processInternal);
    }


    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<Admin> processInternal(Request request) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Updating avatar for admin CorrelationId: {} - AdminId: {}", correlationId, request.adminId().getValue());

            return adminRepository.findById(request.adminId())
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Admin not found")))
                    .filter(Admin::getIsActive)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Account is disabled")))
                    .flatMap(admin -> {
                        String oldAvatarPath = admin.getAvatar();
                        if (request.avatar() == null) {
                            admin.removeAvatar();
                            return avatarStorageService.deleteAvatar(oldAvatarPath)
                                    .then(updateAvatarInDatabase(admin, null));
                        } else if (request.avatar().startsWith("data:image/")) {
                            return avatarStorageService.saveAvatar(admin.getId().getValue(), request.avatar())
                                    .flatMap(result -> {
                                        admin.updateAvatar(result.originalPath());
                                        return avatarStorageService.deleteAvatar(oldAvatarPath)
                                                .then(updateAvatarInDatabase(admin, result.originalPath()));
                                    });
                        } else {
                            return updateAvatarInDatabase(admin, request.avatar());
                        }
                    })
                    .doOnSuccess(admin -> log.info("Avatar updated successfully for admin: {}", admin.getUsername()))
                    .doOnError(error -> log.error("Failed to update avatar for admin: {}: {}",
                            request.adminId().getValue(), error.getMessage()));
        });
    }

    private Mono<Admin> updateAvatarInDatabase(Admin admin, String avatarPath) {
        return adminRepository.updateAvatar(admin.getId(), avatarPath);
    }


    public record Request(AdminId adminId, String avatar) {}

}