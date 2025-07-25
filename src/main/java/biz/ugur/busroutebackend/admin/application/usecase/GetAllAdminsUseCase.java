package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminListResponse;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResponse;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetAllAdminsUseCase implements UseCase<Void, Mono<AdminListResponse>> {

    private final AdminRepository adminRepository;

    public GetAllAdminsUseCase(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @Override
    public Mono<AdminListResponse> execute(Void request) {
        log.debug("Fetching all admins");

        return adminRepository.findAllAdmins()
                .map(this::toResponse)
                .collectList()
                .flatMap(admins -> adminRepository.countActiveAdmins()
                        .map(activeCount -> new AdminListResponse(admins, activeCount)))
                .doOnSuccess(response -> log.debug("Retrieved {} admins ({} active)",
                        response.getAdmins().size(), response.getActiveCount()));
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
