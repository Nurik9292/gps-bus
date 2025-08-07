package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetCurrentAdminUseCase implements UseCase<Mono<GetCurrentAdminUseCase.Query>, Mono<Admin>> {

    private final AdminRepository adminRepository;
    private final CorrelationContextService correlationService;

    public record Query(AdminId adminId) {}

    @Override
    public Mono<Admin> execute(Mono<Query> query) {
        return correlationService.executeWithCorrelation(query.flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<Admin> executeWithCorrelation(Query query) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    String adminId = query.adminId.getValue();
                    log.info("Getting current admin info for - CorrelationId: {} - AdminId: {}",
                            correlationId.value(), query.adminId());

                    return adminRepository.findById(query.adminId())
                            .switchIfEmpty(Mono.error(new AdminNotFoundException(adminId, "id", correlationId)))
                            .doOnSuccess(admin -> log.debug("Current admin info retrieved: {}", admin.getUsername()))
                            .doOnError(error -> log.warn("Failed to get current admin info for {}: {}",adminId, error.getMessage()));
                });
    }


}