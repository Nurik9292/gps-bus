package biz.ugur.busroutebackend.banner.application.usecase.client;

import biz.ugur.busroutebackend.banner.application.dto.BannerList;
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
public class GetBannersWithPaginationByTypeUseCase extends BaseUseCase<Mono<BannerPaginationQuery>, BannerList> {

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
    protected Mono<BannerList> process(Mono<BannerPaginationQuery> query) {
        return query.flatMap(this::processInternal);
    }

    private Mono<BannerList> processInternal(BannerPaginationQuery query){
        log.debug("Fetching banners with pagination client: page={}, size={}, sort={}, order={}, type={}",
                query.getPage(), query.getSize(), query.getSortField(), query.getSortOrder(), query.getType());

        Pageable pageable = createPageable(query);
        BannerType bannerType = BannerType.fromValue(query.getType());

        return bannerRepository.findActiveBannersByTypeWithPagination(bannerType, pageable)
                .flatMap(bannerResponseMapper::toResponse)
                .collectList()
                .zipWhen(banners -> bannerRepository.countByType(bannerType))
                .zipWith(bannerRepository.countActiveBanners())
                .map(tuple -> {
                    var banners = tuple.getT1().getT1();
                    Long totalOfType = tuple.getT1().getT2();
                    Long totalActive = tuple.getT2();

                    return BannerList.of(
                        banners,
                        totalActive,
                        query.getPage(),
                        query.getSize(),
                        totalOfType
                    );
                })
                .doOnSuccess(response -> log.debug("Retrieved {} banners (page {}/{}, {} total active)",
                        response.getBanners().size(),
                        response.getPagination().getCurrentPage(),
                        response.getPagination().getTotalPages(),
                        response.getActiveCount()));
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
