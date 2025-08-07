package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.CreateCommand;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminAlreadyExistsException;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateAdminUseCase implements UseCase<Mono<CreateCommand>, Mono<AdminResult>> {

    private final AdminRepository adminRepository;
    private final EventBus eventBus;
    private final CorrelationContextService correlationService;

    public CreateAdminUseCase(AdminRepository adminRepository,
                              EventBus eventBus,
                              CorrelationContextService correlationService) {
        this.adminRepository = adminRepository;
        this.eventBus = eventBus;
        this.correlationService = correlationService;
    }

    @Override
    public Mono<AdminResult> execute(Mono<CreateCommand> commandMono) {
        return correlationService.executeWithCorrelation(
                commandMono.flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<AdminResult> executeWithCorrelation(CreateCommand command) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.info("Creating admin - CorrelationId: {} - Username: {}",
                            correlationId.value(), command.username());

                    return adminRepository.existsByUsername(command.username())
                            .flatMap(exists -> {
                                if (exists) {
                                    log.warn("Admin creation failed - username exists - CorrelationId: {} - Username: {}",
                                            correlationId.value(), command.username());
                                    return Mono.error(new AdminAlreadyExistsException(command.username(), correlationId));
                                }

                                return adminRepository.save(command.toDomain())
                                        .doOnNext(savedAdmin -> {
                                            savedAdmin.getUncommittedEvents().forEach(eventBus::publish);
                                            savedAdmin.markEventsAsCommitted();
                                        })
                                        .map(AdminResult::fromDomain)
                                        .doOnSuccess(result ->
                                                log.info("Admin created successfully - CorrelationId: {} - Username: {}",
                                                        correlationId.value(), result.username()));
                            });
                });
    }

}
