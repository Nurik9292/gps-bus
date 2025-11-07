package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.application.dto.admin.CreateCommand;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminAlreadyExistsException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
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
public class CreateAdminUseCase extends BaseUseCase<Mono<CreateCommand>, AdminResult> {

    private final AdminRepository adminRepository;
    private final AvatarStorageService avatarStorageService;

    public CreateAdminUseCase(AdminRepository adminRepository,
                              AvatarStorageService avatarStorageService,
                              EventBus eventBus,
                              CorrelationContextService correlationService) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
        this.avatarStorageService = avatarStorageService;
    }


    @Override
    protected Mono<AdminResult> process(Mono<CreateCommand> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }


    private Mono<AdminResult> processInternal(CreateCommand command) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.info("Creating admin - CorrelationId: {} - Username: {}",
                            correlationId.value(), command.username());

                    return adminRepository.existsByUsername(command.username())
                            .flatMap(exists -> {
                                if (exists) {
                                    log.warn("Admin creation failed - username exists - CorrelationId: {} - Username: {}",
                                            correlationId.value(), command.username());
                                    return Mono.error(new AdminAlreadyExistsException(command.username(), correlationId));
                                }

                                Mono<String> avatarMono = processAvatar(command);
                                return avatarMono
                                        .map(avatarPath -> {
                                            log.info("Avatar path after processing: {}", avatarPath);
                                            return new CreateCommand(
                                                    command.username(),
                                                    command.fullName(),
                                                    command.password(),
                                                    avatarPath,
                                                    command.isSuperAdmin(),
                                                    command.isActive()
                                            );
                                        })
                                        .switchIfEmpty(Mono.defer(() -> {
                                            log.info("No avatar processed, using original command with avatar: {}", command.avatar());
                                            return Mono.just(command);
                                        }))
                                        .doOnNext(cmd -> log.info("Command before save - avatar: {}", cmd.avatar()))
                                        .flatMap(cmd -> {
                                            Admin admin = cmd.toDomain();
                                            log.info("Admin domain before save - avatar: {}", admin.getAvatar());
                                            return adminRepository.save(admin);
                                        })
                                        .doOnNext(savedAdmin -> log.info("Admin after save - avatar: {}", savedAdmin.getAvatar()))
                                        .map(AdminResult::fromDomain)
                                        .doOnSuccess(result ->
                                                log.info("Admin created successfully - CorrelationId: {} - Username: {} - Avatar: {}",
                                                        correlationId.value(), result.username(), result.avatar()));
                            });
                });
    }

    private Mono<String> processAvatar(CreateCommand command) {
        String avatar = command.avatar();

        if (avatar == null || avatar.trim().isEmpty()) {
            return Mono.empty();
        }

        if (avatar.startsWith("data:image/")) {
            Admin tempAdmin = command.toDomain();
            String adminId = tempAdmin.getId().getValue();

            return avatarStorageService.save(adminId, avatar)
                    .map(BaseImageStorageService.Result::originalPath)
                    .doOnSuccess(path -> log.info("Avatar saved for new admin: {}", path))
                    .onErrorResume(error -> {
                        log.error("Failed to save avatar for new admin: {}", error.getMessage());
                        return Mono.empty();
                    });
        }

        return Mono.just(avatar);
    }

}
