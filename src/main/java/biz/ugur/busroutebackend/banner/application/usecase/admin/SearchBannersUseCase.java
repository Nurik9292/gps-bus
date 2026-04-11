package biz.ugur.busroutebackend.banner.application.usecase.admin;

import biz.ugur.busroutebackend.banner.application.dto.BannerList;
import biz.ugur.busroutebackend.banner.application.dto.SearchBannersQuery;
import biz.ugur.busroutebackend.banner.application.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.banner.domain.specification.BannerSpecifications;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@Slf4j
public class SearchBannersUseCase extends BaseUseCase<Mono<SearchBannersQuery>, BannerList> {

    private final AdminBannerRepository bannerRepository;
    private final BannerResponseMapper bannerResponseMapper;

    public SearchBannersUseCase(
            AdminBannerRepository bannerRepository,
            CorrelationContextService correlationContextService,
            EventBus eventBus,
            BannerResponseMapper bannerResponseMapper) {
        super(correlationContextService, eventBus);
        this.bannerRepository = bannerRepository;
        this.bannerResponseMapper = bannerResponseMapper;
    }

    @Override
    protected Mono<BannerList> process(Mono<SearchBannersQuery> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<BannerList> processInternal(SearchBannersQuery query) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Searching banners with criteria: {} - CorrelationId: {}", query, correlationId);

            query.validate();

            Specification<Banner> specification = buildSpecification(query);

            Pageable pageable = createPageable(query);

            var bannersFlux = bannerRepository.findBySpecification(specification, pageable)
                    .flatMap(bannerResponseMapper::toResponse);

            var totalMatchingMono = bannerRepository.countBySpecification(specification);

            return bannersFlux
                    .collectList()
                    .zipWith(totalMatchingMono)
                    .zipWith(bannerRepository.countActiveBanners()) 
                    .map(tuple -> {
                        var banners = tuple.getT1().getT1();
                        Long totalMatching = tuple.getT1().getT2();
                        Long activeCount = tuple.getT2();

                        return BannerList.of(
                            banners,
                            activeCount, 
                            query.getPage(),
                            query.getSize(),
                            totalMatching 
                        );
                    })
                    .doOnSuccess(response -> log.debug(
                        "Found {} banners matching criteria (page {}/{}, {} total active)",
                        response.getBanners().size(),
                        response.getPagination().getCurrentPage(),
                        response.getPagination().getTotalPages(),
                        response.getActiveCount()
                    ));
        });
    }


    private Specification<Banner> buildSpecification(SearchBannersQuery query) {
        Specification<Banner> spec = new Specification<Banner>() {
            @Override
            public boolean isSatisfiedBy(Banner banner) {
                return true;
            }

            @Override
            public biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria toSqlCriteria() {
                return biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria.of(
                    "1 = 1", "alwaysTrue", true
                );
            }
        };


        if (query.getType() != null) {
            spec = spec.and(BannerSpecifications.hasType(query.getType()));
            log.debug("Added type filter: {}", query.getType());
        }

        if (Boolean.TRUE.equals(query.getIsActive())) {
            spec = spec.and(BannerSpecifications.isActive());
            log.debug("Added isActive filter");
        } else if (Boolean.FALSE.equals(query.getIsActive())) {
            spec = spec.and(BannerSpecifications.isInactive());
            log.debug("Added isInactive filter");
        }

        if (query.getTitleSearch() != null && !query.getTitleSearch().isBlank()) {
            spec = spec.and(BannerSpecifications.titleContains(query.getTitleSearch()));
            log.debug("Added title search filter: {}", query.getTitleSearch());
        }

        if (Boolean.TRUE.equals(query.getPeriodActive())) {
            spec = spec.and(BannerSpecifications.isPeriodActive(LocalDateTime.now()));
            log.debug("Added periodActive filter");
        }

        if (query.getMinDisplayOrder() != null && query.getMaxDisplayOrder() != null) {
            spec = spec.and(BannerSpecifications.displayOrderBetween(
                query.getMinDisplayOrder(),
                query.getMaxDisplayOrder()
            ));
            log.debug("Added displayOrder range filter: {} - {}",
                query.getMinDisplayOrder(), query.getMaxDisplayOrder());
        }

        if (query.getCreatedAfter() != null) {
            spec = spec.and(BannerSpecifications.createdAfter(query.getCreatedAfter()));
            log.debug("Added createdAfter filter: {}", query.getCreatedAfter());
        }

        if (query.getExpiringWithinDays() != null) {
            spec = spec.and(BannerSpecifications.periodExpiresWithinDays(
                query.getExpiringWithinDays()
            ));
            log.debug("Added expiringWithinDays filter: {}", query.getExpiringWithinDays());
        }

        return spec;
    }


    private Pageable createPageable(SearchBannersQuery query) {
        Sort sort = createSort(query);
        return PageRequest.of(
            query.getPage() - 1,
            query.getSize(),
            sort
        );
    }


    private Sort createSort(SearchBannersQuery query) {
        if (query.getSortField() == null) {
            return Sort.by(Sort.Order.asc("display_order"), Sort.Order.desc("created_at"));
        }

        Sort.Direction direction = "desc".equalsIgnoreCase(query.getSortOrder())
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;

        return Sort.by(direction, query.getSortField());
    }
}
