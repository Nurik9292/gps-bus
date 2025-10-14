package biz.ugur.busroutebackend.banner.appication.usecase.admin;

import biz.ugur.busroutebackend.banner.appication.dto.admin.BannerListResponse;
import biz.ugur.busroutebackend.banner.appication.dto.admin.BannerResponse;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
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

    public GetAllBannersUseCase(AdminBannerRepository bannerRepository,
                                CorrelationContextService correlationContextService,
                                EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.bannerRepository = bannerRepository;
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

            var bannerFlux = activeOnly != null && activeOnly ?
                    bannerRepository.findActiveBanners() :
                    bannerRepository.findAll();

            return bannerFlux
                    .map(this::toResponse)
                    .collectList()
                    .flatMap(banners -> bannerRepository.countActiveBanners()
                            .map(activeCount -> new BannerListResponse(banners, activeCount)))
                    .doOnSuccess(response -> log.debug("Retrieved {} banners ({} active)",
                            response.getBanners().size(), response.getActiveCount()));
        });
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