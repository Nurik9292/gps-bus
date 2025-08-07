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
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UpdateAdminUseCase implements UseCase<Mono<UpdateAdminUseCase.Request>, Mono<AdminResult>> {

    private final AdminRepository adminRepository;
    private final EventBus eventBus;
    private final CorrelationContextService correlationService;

    public UpdateAdminUseCase(AdminRepository adminRepository, EventBus eventBus, CorrelationContextService correlationService) {
        this.adminRepository = adminRepository;
        this.eventBus = eventBus;
        this.correlationService = correlationService;
    }

    @Override
    public Mono<AdminResult> execute(Mono<Request> request) {
        return correlationService.executeWithCorrelation(request.flatMap(this::executeWithCorrelation), "admin");
    }

    public Mono<AdminResult> executeWithCorrelation(Request request) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.info("Updating admin - CorrelationId: {} - AdminId: {}",
                            correlationId.value(), request.adminId());

                    return adminRepository.findById(AdminId.of(request.adminId()))
                            .switchIfEmpty(Mono.defer(() -> {
                                log.warn("Admin not found - CorrelationId: {} - AdminId: {}",
                                        correlationId.value(), request.adminId());
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
                                                        .doOnNext(this::publishDomainEvents)
                                                        .map(AdminResult::fromDomain)
                                                        .doOnSuccess(result -> log.info("Admin updated successfully - CorrelationId: {} - Username: {}",
                                                                correlationId.value(), result.username()));
                                            })
                            )
                            .doOnError(error -> log.error("Failed to update admin - CorrelationId: {} - AdminId: {}",
                                    correlationId.value(), request.adminId(), error));
                });
    }

    private Mono<Admin> applyUpdates(Admin admin, UpdateCommand command) {
        admin.updateProfile(command.username(), command.fullName());

        if (command.newPassword() != null && !command.newPassword().trim().isEmpty()) {
            admin.changePassword(command.newPassword());
        }

        if (command.isActive() != null) {
            if (command.isActive()) admin.activate();
            else admin.deactivate();
        }

        return Mono.just(admin);
    }

    private void publishDomainEvents(Admin admin) {
        admin.getUncommittedEvents().forEach(eventBus::publish);
        admin.markEventsAsCommitted();
    }

    public record Request(String adminId, UpdateCommand command) {}
}


