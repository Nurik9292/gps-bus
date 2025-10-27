package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.application.dto.admin.UpdateCommand;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminAlreadyExistsException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UpdateAdminUseCase extends BaseUseCase<Mono<UpdateAdminUseCase.Request>, AdminResult> {

    private final AdminRepository adminRepository;

    public UpdateAdminUseCase(AdminRepository adminRepository, EventBus eventBus, CorrelationContextService correlationService) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
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

                            return applyUpdates(existingAdmin, request.command())
                                .flatMap(adminRepository::save)
                                .map(AdminResult::fromDomain)
                                .doOnSuccess(result ->
                                    log.info("Admin updated successfully - CorrelationId: {} - Username: {}",
                                        correlationId.value(), result.username()));
                                })
                    )
                    .doOnError(error -> log.error("Failed to update admin - CorrelationId: {} - AdminId: {}",
                                    correlationId.value(), request.adminId(), error));
        });
    }

    private Mono<Admin> applyUpdates(Admin admin, UpdateCommand command) {
        // Admin is immutable - each method returns a new instance
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

        return Mono.just(updatedAdmin);
    }


    public record Request(String adminId, UpdateCommand command) {}
}


