package biz.ugur.busroutebackend.banner.appication.usecase.admin;

import biz.ugur.busroutebackend.banner.appication.dto.admin.BannerListResponse;
import biz.ugur.busroutebackend.banner.appication.dto.admin.BannerPaginationQuery;
import biz.ugur.busroutebackend.banner.appication.dto.admin.BannerResponse;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class GetBannersWithPaginationUseCase extends BaseUseCase<Mono<BannerPaginationQuery>, BannerListResponse> {

    private final AdminBannerRepository bannerRepository;

    public GetBannersWithPaginationUseCase(AdminBannerRepository bannerRepository,
                                           CorrelationContextService correlationContextService,
                                           EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.bannerRepository = bannerRepository;
    }


    @Override
    protected Mono<BannerListResponse> process(Mono<BannerPaginationQuery> query) {
        return query.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<BannerListResponse> processInternal(BannerPaginationQuery query) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Fetching banners with pagination admin: page={}, size={}, sort={}, order={}, active={}",
                    query.getPage(), query.getSize(), query.getSortField(), query.getSortOrder(), query.getActiveOnly());

            Pageable pageable = createPageable(query);

            return  bannerRepository.findAll(pageable)
                    .map(this::toResponse)
                    .collectList()
                    .zipWith(bannerRepository.countActiveBanners())
                    .map(tuple -> {
                        List<BannerResponse> banners = tuple.getT1();
                        Long activeCount = tuple.getT2();

                        return new BannerListResponse(banners, activeCount,banners.size() == query.getSize());
                    })
                    .doOnSuccess(response -> log.debug("Retrieved {} banners ({} active, {} total)",
                            response.getBanners().size(), response.getActiveCount(), response.getTotalCount()));
        });
    }

    private Pageable createPageable(BannerPaginationQuery query) {
        Sort sort = Sort.by(
                query.getSortOrder().equalsIgnoreCase("desc") ?
                        Sort.Direction.DESC : Sort.Direction.ASC,
                query.getSortField()
        );

        return PageRequest.of(query.getPage() - 1, query.getSize(), sort);
    }

    private BannerResponse toResponse(Banner banner) {
        return new BannerResponse(
                banner.getId().getValue(),
                banner.getTitle(),
                banner.getType().getValue(),
                banner.getImageUrl(),
                banner.getTargetUrl(),
                banner.getIsActive(),
                banner.getDisplayOrder(),
                banner.getStartDate(),
                banner.getEndDate(),
                banner.getContent()
        );
    }

}