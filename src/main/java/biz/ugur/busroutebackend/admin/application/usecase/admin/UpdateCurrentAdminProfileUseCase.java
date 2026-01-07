package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.domain.services.PasswordEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class UpdateCurrentAdminProfileUseCase extends BaseUseCase<Mono<UpdateCurrentAdminProfileUseCase.Request>, Admin> {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public UpdateCurrentAdminProfileUseCase(AdminRepository adminRepository,
                                            PasswordEncoder passwordEncoder,
                                            EventBus eventBus,
                                            CorrelationContextService correlationService) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
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
                                Admin updatedAdmin = admin.updateProfile(request.username(), request.fullName());

                                // Update password if provided
                                if (request.newPassword() != null && !request.newPassword().trim().isEmpty()) {
                                    log.info("Updating password for admin: {}", adminId);
                                    String encodedPassword = passwordEncoder.encode(request.newPassword());
                                    updatedAdmin = updatedAdmin.changePassword(encodedPassword);
                                }

                                return adminRepository.save(updatedAdmin);
                            })
                            .doOnSuccess(admin -> log.info("Profile updated successfully for admin: {}", admin.getUsername()))
                            .doOnError(error -> log.error("Failed to update profile for admin: {}: {}",
                                    adminId, error.getMessage()));
                });
    }

    public record Request(AdminId adminId, String username, String fullName, String newPassword) {}
}