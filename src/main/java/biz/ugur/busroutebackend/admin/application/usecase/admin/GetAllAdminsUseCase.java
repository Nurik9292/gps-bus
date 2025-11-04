package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminList;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetAllAdminsUseCase extends BaseUseCase<Mono<Void>, AdminList> {

    private final AdminRepository adminRepository;

    public GetAllAdminsUseCase(AdminRepository adminRepository,
                               CorrelationContextService correlationContextService,
                               EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.adminRepository = adminRepository;
    }

    @Override
    protected Mono<AdminList> process(Mono<Void> request) {
        return request.then(Mono.defer(this::processInternal));
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<AdminList> processInternal() {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Getting admins from correlationId {}", correlationId);

            return adminRepository.findAll()
                    .map(AdminResult::fromDomain)
                    .collectList()
                    .zipWith(adminRepository.countActiveAdmins())
                    .zipWith(adminRepository.count())
                    .map(tuple -> {
                        var admins = tuple.getT1().getT1();
                        Long activeCount = tuple.getT1().getT2();
                        Long totalCount = tuple.getT2();

                        // Since this endpoint doesn't use pagination params, return all items on page 1
                        return new AdminList(
                                admins,
                                activeCount,
                                1,  // current page
                                admins.size(),  // page size (all items)
                                totalCount  // total items in database
                        );
                    })
                    .doOnSuccess(response -> log.debug(
                            "Retrieved {} admins on page {} ({} active, {} total)",
                            response.getAdmins().size(),
                            response.getPagination().getCurrentPage(),
                            response.getActiveCount(),
                            response.getPagination().getTotalItems()
                    ));
        });
    }
}
