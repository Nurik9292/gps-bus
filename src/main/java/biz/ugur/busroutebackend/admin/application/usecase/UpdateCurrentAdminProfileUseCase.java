package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateCurrentAdminProfileUseCase implements UseCase<UpdateCurrentAdminProfileUseCase.Request, Mono<Admin>> {

    private final AdminRepository adminRepository;
    private final EventBus eventBus;

    public record Request(AdminId adminId, String fullName, String avatar) {}

    @Override
    public Mono<Admin> execute(Request request) {
        log.info("Updating profile for admin: {}", request.adminId().getValue());

        return adminRepository.findById(request.adminId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Admin not found")))
                .filter(Admin::getIsActive)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Account is disabled")))
                .flatMap(admin -> {
                    admin.updateProfile(request.fullName(), request.avatar());

                    return adminRepository.save(admin)
                            .doOnNext(savedAdmin -> {
                                savedAdmin.getUncommittedEvents().forEach(eventBus::publish);
                                savedAdmin.markEventsAsCommitted();
                            });
                })
                .doOnSuccess(admin -> log.info("Profile updated successfully for admin: {}", admin.getUsername()))
                .doOnError(error -> log.error("Failed to update profile for admin: {}: {}",
                        request.adminId().getValue(), error.getMessage()));
    }


}