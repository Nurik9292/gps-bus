package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminList;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetAllAdminsUseCase implements UseCase<Mono<Void>, Mono<AdminList>> {

    private final AdminRepository adminRepository;

    public GetAllAdminsUseCase(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @Override
    public Mono<AdminList> execute(Mono<Void> request) {
        log.debug("Fetching all admins");
        return request.thenMany(adminRepository.findAllAdmins())
                .map(AdminResult::fromDomain)
                .collectList()
                .flatMap(admins -> adminRepository.countActiveAdmins()
                        .map(activeCount -> new AdminList(admins, activeCount)))
                .doOnSuccess(response -> log.debug("Retrieved {} admins ({} active)",
                        response.getAdmins().size(), response.getActiveCount()));
    }

}
