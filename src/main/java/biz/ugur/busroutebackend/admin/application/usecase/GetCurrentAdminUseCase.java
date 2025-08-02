package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetCurrentAdminUseCase implements UseCase<GetCurrentAdminUseCase.Query, Mono<Admin>> {

    private final AdminRepository adminRepository;

    public record Query(AdminId adminId) {}

    @Override
    public Mono<Admin> execute(Query query) {
        log.debug("Getting current admin info for: {}", query.adminId().getValue());

        return adminRepository.findById(query.adminId())
                .switchIfEmpty(Mono.error(new AdminNotFoundException("Admin not found")))
                .filter(Admin::getIsActive)
                .switchIfEmpty(Mono.error(new AdminNotFoundException("Account is disabled")))
                .doOnSuccess(admin -> log.debug("Current admin info retrieved: {}", admin.getUsername()))
                .doOnError(error -> log.warn("Failed to get current admin info for {}: {}", query.adminId().getValue(), error.getMessage()));
    }


}