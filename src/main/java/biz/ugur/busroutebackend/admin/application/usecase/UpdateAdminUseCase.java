package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResponse;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminUpdateRequest;
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
public class UpdateAdminUseCase implements UseCase<UpdateAdminUseCase.Request, Mono<AdminResponse>> {

    private final AdminRepository adminRepository;
    private final EventBus eventBus;

    public UpdateAdminUseCase(AdminRepository adminRepository, EventBus eventBus) {
        this.adminRepository = adminRepository;
        this.eventBus = eventBus;
    }

    @Override
    public Mono<AdminResponse> execute(Request request) {
        log.info("Updating admin: {}", request.adminId);

        return adminRepository.findById(AdminId.of(request.adminId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Admin not found: " + request.adminId)))
                .flatMap(admin -> {
                    admin.updateProfile(request.updateRequest.getFullName());

                    if (request.updateRequest.getNewPassword() != null && !request.updateRequest.getNewPassword().trim().isEmpty()) {
                        admin.changePassword(request.updateRequest.getNewPassword());
                    }

                    if (request.updateRequest.getIsActive() != null) {
                        if (request.updateRequest.getIsActive()) {
                            admin.activate();
                        } else {
                            admin.deactivate();
                        }
                    }

                    return adminRepository.save(admin)
                            .doOnNext(savedAdmin -> {
                                savedAdmin.getUncommittedEvents().forEach(eventBus::publish);
                                savedAdmin.markEventsAsCommitted();
                            });
                })
                .map(this::toResponse)
                .doOnSuccess(response -> log.info("Admin updated successfully: {}", response.getUsername()))
                .doOnError(error -> log.error("Failed to update admin: {}", request.adminId, error));
    }

    private AdminResponse toResponse(Admin admin) {
        return new AdminResponse(
                admin.getId().getValue(),
                admin.getUsername(),
                admin.getFullName(),
                admin.getIsActive(),
                admin.getIsSuperAdmin(),
                admin.getLastLoginAt()
        );
    }

    public record Request(String adminId, AdminUpdateRequest updateRequest) {}
}


