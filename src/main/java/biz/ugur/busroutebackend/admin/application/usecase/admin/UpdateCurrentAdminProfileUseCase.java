package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateCurrentAdminProfileUseCase implements UseCase<Mono<UpdateCurrentAdminProfileUseCase.Request>, Mono<Admin>> {

    private final AdminRepository adminRepository;
    private final EventBus eventBus;
    private final CorrelationContextService correlationService;

    public record Request(AdminId adminId, String username, String fullName) {}

    @Override
    public Mono<Admin> execute(Mono<UpdateCurrentAdminProfileUseCase.Request> request) {
        return correlationService.executeWithCorrelation(request.flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<Admin> executeWithCorrelation(Request request) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId ->  {
                    String adminId = request.adminId().getValue();
                    log.info("Updating profile for admin - CorrelationId: {} - AdminId {}", correlationId.value(), adminId);

                    return adminRepository.findById(request.adminId())
                            .switchIfEmpty(Mono.error(new AdminNotFoundException(adminId, "id", correlationId)))
                            .flatMap(admin -> {
                                admin.updateProfile(request.username(), request.fullName());
                                return adminRepository.save(admin)
                                        .doOnNext(savedAdmin -> {
                                            savedAdmin.getUncommittedEvents().forEach(eventBus::publish);
                                            savedAdmin.markEventsAsCommitted();
                                        });
                            })
                            .doOnSuccess(admin -> log.info("Profile updated successfully for admin: {}", admin.getUsername()))
                            .doOnError(error -> log.error("Failed to update profile for admin: {}: {}",
                                    adminId, error.getMessage()));
                });
    }
}