package biz.ugur.busroutebackend.admin.application.usecase.banner;

import biz.ugur.busroutebackend.admin.application.dto.banner.BannerListResponse;
import biz.ugur.busroutebackend.admin.application.dto.banner.BannerResponse;
import biz.ugur.busroutebackend.admin.domain.model.Banner;
import biz.ugur.busroutebackend.admin.domain.repository.BannerRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetBannersByTypeUseCase extends BaseUseCase<Mono<String>, BannerListResponse> {

    private final BannerRepository bannerRepository;

    protected GetBannersByTypeUseCase(CorrelationContextService correlationService,
                                      EventBus eventBus,
                                      BannerRepository bannerRepository) {
        super(correlationService, eventBus);
        this.bannerRepository = bannerRepository;
    }

    @Override
    protected Mono<BannerListResponse> process(Mono<String> request) {
        return request.flatMap(this::executeWithType);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<BannerListResponse> executeWithType(String type) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Getting banners by Type: {} - CorrelationId: {}", type, correlationId);

            return bannerRepository.findByTypeAndActive(type)
                    .map(this::toResponse)
                    .collectList()
                    .flatMap(banners -> bannerRepository.countByType(type)
                            .map(totalCount -> new BannerListResponse(banners, totalCount)))
                    .doOnSuccess(response -> log.debug("Retrieved {} banners of type {} ({} total)",
                            response.getBanners().size(), type, response.getActiveCount()))
                    .onErrorMap(error -> {
                        log.error("Failed to get banners by type {}: {}", type, error.getMessage());
                        return new RuntimeException("Banners not found for type: " + type);
                    });
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
                banner.getEndDate()
        );
    }
    }