package biz.ugur.busroutebackend.banner.application.usecase.admin;

import biz.ugur.busroutebackend.banner.application.dto.BannerListResponse;
import biz.ugur.busroutebackend.banner.application.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetAllBannersUseCase extends BaseUseCase<Mono<Boolean>, BannerListResponse> {

    private final AdminBannerRepository bannerRepository;
    private final BannerResponseMapper bannerResponseMapper;

    public GetAllBannersUseCase(AdminBannerRepository bannerRepository,
                                CorrelationContextService correlationContextService,
                                EventBus eventBus,
                                BannerResponseMapper bannerResponseMapper) {
        super(correlationContextService, eventBus);
        this.bannerRepository = bannerRepository;
        this.bannerResponseMapper = bannerResponseMapper;
    }


    @Override
    protected Mono<BannerListResponse> process(Mono<Boolean> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<BannerListResponse> processInternal(Boolean activeOnly) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Fetching banners (activeOnly: {}) - CorrelationId: {}", activeOnly, correlationId);

            var bannerFlux = activeOnly ?
                    bannerRepository.findActiveBanners() :
                    bannerRepository.findAll();

            return bannerFlux
                    .flatMap(bannerResponseMapper::toResponse)
                    .collectList()
                    .flatMap(banners -> bannerRepository.countActiveBanners()
                            .map(activeCount -> new BannerListResponse(banners, activeCount)))
                    .doOnSuccess(response -> log.debug("Retrieved {} banners ({} active)",
                            response.getBanners().size(), response.getActiveCount()));
        });
    }
}