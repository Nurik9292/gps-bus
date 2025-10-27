package biz.ugur.busroutebackend.banner.application.usecase.client;

import biz.ugur.busroutebackend.banner.application.dto.BannerListResponse;
import biz.ugur.busroutebackend.banner.application.dto.BannerPaginationQuery;
import biz.ugur.busroutebackend.banner.application.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.repository.ClientBannerRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@Slf4j
public class GetBannersWithPaginationByTypeUseCase extends BaseUseCase<Mono<BannerPaginationQuery>, BannerListResponse> {

    private final ClientBannerRepository bannerRepository;
    private final BannerResponseMapper bannerResponseMapper;

    public GetBannersWithPaginationByTypeUseCase(CorrelationContextService correlationService,
                                                 EventBus eventBus,
                                                 ClientBannerRepository clientBannerRepository,
                                                 BannerResponseMapper bannerResponseMapper) {
        super(correlationService, eventBus);
        this.bannerRepository = clientBannerRepository;
        this.bannerResponseMapper = bannerResponseMapper;
    }

    @Override
    protected String getBoundContext() {
        return "client";
    }

    @Override
    protected Mono<BannerListResponse> process(Mono<BannerPaginationQuery> query) {
        return query.flatMap(this::processInternal);
    }

    private Mono<BannerListResponse> processInternal(BannerPaginationQuery query){
        log.debug("Fetching banners with pagination client: page={}, size={}, sort={}, order={}, active={}",
                query.getPage(), query.getSize(), query.getSortField(), query.getSortOrder(), query.getActiveOnly());

        Pageable pageable = createPageable(query);

        return bannerRepository.findActiveBannersByTypeWithPagination(BannerType.fromValue(query.getType()), pageable)
                .flatMap(bannerResponseMapper::toResponse)
                .collectList()
                .map(banners -> {
                    return new BannerListResponse(banners, (long) banners.size(),banners.size() == query.getSize());
                })  .doOnSuccess(response -> log.debug("Retrieved {} banners ({} active, {} total)",
                        response.getBanners().size(), response.getActiveCount(), response.getTotalCount()));
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
