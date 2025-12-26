package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminList;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminPaginationQuery;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.specification.AdminSpecifications;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.application.util.PaginationHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class GetAllAdminsUseCase extends BaseUseCase<Mono<AdminPaginationQuery>, AdminList> {

    private final AdminRepository adminRepository;

    public GetAllAdminsUseCase(AdminRepository adminRepository,
                               CorrelationContextService correlationContextService,
                               EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.adminRepository = adminRepository;
    }

    @Override
    protected Mono<AdminList> process(Mono<AdminPaginationQuery> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<AdminList> processInternal(AdminPaginationQuery query) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Getting admins from correlationId {}", correlationId);

            Pageable pageable = createPageable(query);
            Specification<Admin> specification = buildSpecification(query);

            Mono<List<Admin>> adminsMono = (specification != null)
                    ? adminRepository.findBySpecification(specification, pageable).collectList()
                    : adminRepository.findAll().collectList();

            Mono<Long> totalCountMono = (specification != null)
                    ? adminRepository.countBySpecification(specification)
                    : adminRepository.count();

            return adminsMono
                    .zipWith(adminRepository.countActiveAdmins())
                    .zipWith(totalCountMono)
                    .map(tuple -> {
                        List<Admin> admins = tuple.getT1().getT1();
                        Long activeCount = tuple.getT1().getT2();
                        Long totalCount = tuple.getT2();

                        List<AdminResult> adminResults = admins.stream()
                                .map(AdminResult::fromDomain)
                                .toList();

                        return new AdminList(
                                adminResults,
                                activeCount,
                                query.page(),
                                query.size(),
                                totalCount
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

    private Specification<Admin> buildSpecification(AdminPaginationQuery query) {
        Specification<Admin> spec = null;

        if (query.searchQuery() != null && !query.searchQuery().isBlank()) {
            spec = AdminSpecifications.hasUsername(query.searchQuery());
        }

        if (query.isActivate() != null) {
            Specification<Admin> activeSpec = query.isActivate()
                    ? AdminSpecifications.isActive()
                    : AdminSpecifications.isInactive();

            spec = (spec == null) ? activeSpec : spec.and(activeSpec);
        }



        return spec;
    }

    private Pageable createPageable(AdminPaginationQuery query) {
        return PaginationHelper.createPageable(
                query.page(), query.size(), query.sortField(), query.sortOrder()
        );
    }
}
