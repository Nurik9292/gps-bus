package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.application.dto.admin.UpdateCommand;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
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

    public UpdateAdminUseCase(AdminRepository adminRepository, EventBus eventBus) {
        this.adminRepository = adminRepository;
        this.eventBus = eventBus;
    }

    @Override
    public Mono<AdminResult> execute(Mono<Request> requestMono) {
        return requestMono.flatMap(request ->
                adminRepository.findById(AdminId.of(request.adminId()))
                        .switchIfEmpty(Mono.error(new AdminNotFoundException("Admin not found: " + request.adminId())))
                        .flatMap(admin -> applyUpdates(admin, request.command())
                                .flatMap(adminRepository::save)
                                .doOnNext(this::publishDomainEvents))
                        .map(AdminResult::fromDomain)
                        .doOnSuccess(result -> log.info("Admin updated successfully: {}", result.username()))
                        .doOnError(error -> log.error("Failed to update admin: {}", request.adminId(), error))
        );
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


