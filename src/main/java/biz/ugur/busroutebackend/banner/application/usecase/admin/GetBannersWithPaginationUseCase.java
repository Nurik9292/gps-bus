package biz.ugur.busroutebackend.banner.application.usecase.admin;

import biz.ugur.busroutebackend.banner.application.dto.BannerList;
import biz.ugur.busroutebackend.banner.application.dto.BannerPaginationQuery;
import biz.ugur.busroutebackend.banner.application.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.banner.domain.specification.BannerSpecifications;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.application.util.PaginationHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class GetBannersWithPaginationUseCase extends BaseUseCase<Mono<BannerPaginationQuery>, BannerList> {

    private final AdminBannerRepository bannerRepository;
    private final BannerResponseMapper bannerResponseMapper;

    public GetBannersWithPaginationUseCase(AdminBannerRepository bannerRepository,
                                           CorrelationContextService correlationContextService,
                                           EventBus eventBus,
                                           BannerResponseMapper bannerResponseMapper) {
        super(correlationContextService, eventBus);
        this.bannerRepository = bannerRepository;
        this.bannerResponseMapper = bannerResponseMapper;
    }


    @Override
    protected Mono<BannerList> process(Mono<BannerPaginationQuery> query) {
        return query.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<BannerList> processInternal(BannerPaginationQuery query) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Fetching banners with pagination CorrelationId: {} - admin: page={}, size={}, sort={}, order={}, active={}",
                    correlationId, query.getPage(), query.getSize(), query.getSortField(), query.getSortOrder(), query.getActiveOnly());

            Pageable pageable = createPageable(query);
            Specification<Banner> specification = buildSpecification(query);

            Mono<List<Banner>> bannersMono = (specification != null)
                    ? bannerRepository.findBySpecification(specification, pageable).collectList()
                    : bannerRepository.findAll(pageable).collectList();

            Mono<Long> totalCountMono = (specification != null)
                    ? bannerRepository.countBySpecification(specification)
                    : bannerRepository.count();

            Mono<Long> activeCountMono = bannerRepository.countActiveBanners();

            return Mono.zip(bannersMono, activeCountMono, totalCountMono)
                    .flatMap(tuple -> {
                        List<Banner> banners = tuple.getT1();
                        Long activeCount = tuple.getT2();
                        Long totalCount = tuple.getT3();

                        return Flux.fromIterable(banners)
                                .flatMap(bannerResponseMapper::toResponse)
                                .collectList()
                                .map(bannerResponses -> BannerList.of(
                                        bannerResponses,
                                        activeCount,
                                        query.getPage(),
                                        query.getSize(),
                                        totalCount
                                ));
                    })
                    .doOnSuccess(response -> log.debug(
                            "CorrelationId: {} - Retrieved {} banners out of {} total ({} active)",
                            correlationId,
                            response.getBanners().size(),
                            response.getPagination().getTotalItems(),
                            response.getActiveCount()
                    ));
        });
    }

    private Pageable createPageable(BannerPaginationQuery query) {
        return PaginationHelper.createPageable(
                query.getPage(), query.getSize(), query.getSortField(), query.getSortOrder()
        );
    }


    private Specification<Banner> buildSpecification(BannerPaginationQuery query) {
        Specification<Banner> spec = null;

        if (query.getQuery() != null && !query.getQuery().isBlank()) {
            spec = BannerSpecifications.titleContains(query.getQuery());
        }

        if (Boolean.TRUE.equals(query.getActiveOnly())) {
            Specification<Banner> activeSpec = BannerSpecifications.isReadyForDisplay();
            spec = (spec == null) ? activeSpec : spec.and(activeSpec);
        }

        return spec;
    }

}