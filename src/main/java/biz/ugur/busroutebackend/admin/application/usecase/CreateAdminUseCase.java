package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.application.dto.admin.CreateCommand;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateAdminUseCase implements UseCase<CreateCommand, Mono<AdminResult>> {

    private final AdminRepository adminRepository;
    private final EventBus eventBus;

    public CreateAdminUseCase(AdminRepository adminRepository, EventBus eventBus) {
        this.adminRepository = adminRepository;
        this.eventBus = eventBus;
    }

    @Override
    public Mono<AdminResult> execute(CreateCommand command) {
        log.info("Creating new admin: {}", command.username());

        return adminRepository.existsByUsername(command.username())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("Username already exists: " + command.username()));
                    }
                    return adminRepository.save(command.toDomain())
                            .doOnNext(savedAdmin -> {
                                savedAdmin.getUncommittedEvents().forEach(eventBus::publish);
                                savedAdmin.markEventsAsCommitted();
                            })
                            .map(AdminResult::fromDomain);
                })
                .doOnSuccess(response -> log.info("Admin created successfully: {}", response.username()))
                .doOnError(error -> log.error("Failed to create admin: {}", command.username(), error));
    }


}
