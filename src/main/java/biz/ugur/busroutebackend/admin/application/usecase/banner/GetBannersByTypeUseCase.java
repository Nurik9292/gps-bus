package biz.ugur.busroutebackend.admin.application.usecase.banner;

import biz.ugur.busroutebackend.admin.application.dto.banner.BannerListResponse;
import biz.ugur.busroutebackend.admin.application.dto.banner.BannerResponse;
import biz.ugur.busroutebackend.admin.domain.model.Banner;
import biz.ugur.busroutebackend.admin.domain.repository.BannerRepository;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetBannersByTypeUseCase implements UseCase<Mono<String>, Mono<BannerListResponse>> {

    private final BannerRepository bannerRepository;

    @Override
    public Mono<BannerListResponse> execute(Mono<String> typeMono) {
        return typeMono.flatMap(this::executeWithType);
    }

    private Mono<BannerListResponse> executeWithType(String type) {
        log.debug("Getting banners by type: {}", type);

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
    }

    private BannerResponse toResponse(Banner banner) {
        return new BannerResponse(
                banner.getId().getValue(),
                banner.getTitle(),
                banner.getType(),
                banner.getImageUrl(),
                banner.getTargetUrl(),
                banner.getIsActive(),
                banner.getDisplayOrder(),
                banner.getStartDate(),
                banner.getEndDate()
        );
    }
    }