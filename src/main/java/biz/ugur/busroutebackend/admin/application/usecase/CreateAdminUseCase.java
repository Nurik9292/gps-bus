package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminCreateRequest;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResponse;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateAdminUseCase implements UseCase<AdminCreateRequest, Mono<AdminResponse>> {

    private final AdminRepository adminRepository;
    private final EventBus eventBus;

    public CreateAdminUseCase(AdminRepository adminRepository, EventBus eventBus) {
        this.adminRepository = adminRepository;
        this.eventBus = eventBus;
    }

    @Override
    public Mono<AdminResponse> execute(AdminCreateRequest request) {
        log.info("Creating new admin: {}", request.getUsername());

        return adminRepository.existsByUsername(request.getUsername())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("Username already exists: " + request.getUsername()));
                    }

                    Admin admin = new Admin(
                            request.getUsername(),
                            request.getPassword(),
                            request.getFullName(),
                            request.getIsSuperAdmin()
                    );

                    return adminRepository.save(admin)
                            .doOnNext(savedAdmin -> {
                                savedAdmin.getUncommittedEvents().forEach(eventBus::publish);
                                savedAdmin.markEventsAsCommitted();
                            })
                            .map(this::toResponse);
                })
                .doOnSuccess(response -> log.info("Admin created successfully: {}", response.getUsername()))
                .doOnError(error -> log.error("Failed to create admin: {}", request.getUsername(), error));
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
}
