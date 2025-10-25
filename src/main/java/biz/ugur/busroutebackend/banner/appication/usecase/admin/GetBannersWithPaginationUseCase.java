package biz.ugur.busroutebackend.banner.appication.usecase.admin;

import biz.ugur.busroutebackend.banner.appication.dto.BannerListResponse;
import biz.ugur.busroutebackend.banner.appication.dto.BannerPaginationQuery;
import biz.ugur.busroutebackend.banner.appication.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.appication.mapper.BannerResponseMapper;
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
    protected Mono<BannerListResponse> process(Mono<BannerPaginationQuery> query) {
        return query.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<BannerListResponse> processInternal(BannerPaginationQuery query) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Fetching banners with pagination CorrelationId: {} - admin: page={}, size={}, sort={}, order={}, active={}",
                    correlationId, query.getPage(), query.getSize(), query.getSortField(), query.getSortOrder(), query.getActiveOnly());

            Pageable pageable = createPageable(query);

            return  bannerRepository.findAll(pageable)
                    .flatMap(bannerResponseMapper::toResponse)
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

}