package biz.ugur.busroutebackend.banner.appication.usecase.admin;

import biz.ugur.busroutebackend.banner.appication.dto.BannerListResponse;
import biz.ugur.busroutebackend.banner.appication.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetBannersByTypeUseCase extends BaseUseCase<Mono<String>, BannerListResponse> {

    private final AdminBannerRepository bannerRepository;
    private final BannerResponseMapper bannerResponseMapper;

    protected GetBannersByTypeUseCase(CorrelationContextService correlationService,
                                      EventBus eventBus,
                                      AdminBannerRepository bannerRepository,
                                      BannerResponseMapper bannerResponseMapper) {
        super(correlationService, eventBus);
        this.bannerRepository = bannerRepository;
        this.bannerResponseMapper = bannerResponseMapper;
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
        BannerType bannerType = BannerType.fromValue(type);

        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Get banner by type CorrelationId: {} - Banner Type: {}", correlationId, type);
            return bannerRepository.findByTypeAndActive(bannerType)
                    .flatMap(bannerResponseMapper::toResponse)
                    .collectList()
                    .flatMap(banners -> bannerRepository.countByType(bannerType)
                            .map(totalCount -> new BannerListResponse(banners, totalCount)))
                    .doOnSuccess(response -> log.debug("Retrieved {} banners of type {} ({} total)",
                            response.getBanners().size(), type, response.getActiveCount()))
                    .onErrorMap(error -> {
                        log.error("Failed to get banners by type {}: {}", type, error.getMessage());
                        return new RuntimeException("Banners not found for type: " + type);
                    });
        });
    }
}