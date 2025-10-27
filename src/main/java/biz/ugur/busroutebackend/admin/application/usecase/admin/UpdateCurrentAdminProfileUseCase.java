package biz.ugur.busroutebackend.admin.application.usecase.admin;

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

@Slf4j
@Service
public class UpdateCurrentAdminProfileUseCase extends BaseUseCase<Mono<UpdateCurrentAdminProfileUseCase.Request>, Admin> {

    private final AdminRepository adminRepository;

    public UpdateCurrentAdminProfileUseCase(AdminRepository adminRepository,
                                            EventBus eventBus,
                                            CorrelationContextService correlationService) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
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
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId ->  {
                    String adminId = request.adminId().getValue();
                    log.info("Updating profile for admin - CorrelationId: {} - AdminId {}", correlationId.value(), adminId);

                    return adminRepository.findById(request.adminId())
                            .switchIfEmpty(Mono.error(new AdminNotFoundException(adminId, "id", correlationId)))
                            .flatMap(admin -> {
                                // Admin is immutable - updateProfile() returns a new instance
                                Admin updatedAdmin = admin.updateProfile(request.username(), request.fullName());
                                return adminRepository.save(updatedAdmin);
                            })
                            .doOnSuccess(admin -> log.info("Profile updated successfully for admin: {}", admin.getUsername()))
                            .doOnError(error -> log.error("Failed to update profile for admin: {}: {}",
                                    adminId, error.getMessage()));
                });
    }

    public record Request(AdminId adminId, String username, String fullName) {}
}