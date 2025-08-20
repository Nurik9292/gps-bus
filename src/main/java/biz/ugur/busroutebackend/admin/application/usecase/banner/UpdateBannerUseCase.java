package biz.ugur.busroutebackend.admin.application.usecase.banner;

import biz.ugur.busroutebackend.admin.application.dto.banner.BannerResponse;
import biz.ugur.busroutebackend.admin.application.dto.banner.BannerUpdateRequest;
import biz.ugur.busroutebackend.admin.domain.model.Banner;
import biz.ugur.busroutebackend.admin.domain.repository.BannerRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.storage.BannerStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Service
@Slf4j
public class UpdateBannerUseCase extends BaseUseCase<Mono<UpdateBannerUseCase.Request>, BannerResponse> {

    private final BannerRepository bannerRepository;
    private final BannerStorageService bannerStorageService;

    public UpdateBannerUseCase(BannerRepository bannerRepository,
                               EventBus eventBus,
                               CorrelationContextService correlationContextService,
                               BannerStorageService bannerStorageService) {
        super(correlationContextService, eventBus);
        this.bannerRepository = bannerRepository;
        this.bannerStorageService = bannerStorageService;
    }


    @Override
    protected Mono<BannerResponse> process(Mono<Request> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<BannerResponse> processInternal(Request request) {
        return correlationService.getCurrentCorrelationId()
                .doOnNext(correlationId -> log.info(
                        "Updating banner: CorrelationId - {} BannerId - {}",
                        correlationId, request.bannerId
                ))
                .then(bannerRepository.findById(BannerId.of(request.bannerId)))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Banner not found: " + request.bannerId)))
                .flatMap(banner -> handleImageUpdate(banner, request.updateData))
                .flatMap(banner -> updateBannerFields(banner, request.updateData))
                .flatMap(bannerRepository::save)
                .map(this::toResponse)
                .doOnSuccess(response -> log.info("Banner updated successfully: {}", response.getTitle()))
                .doOnError(error -> log.error("Failed to update banner: {}", request.bannerId, error));
    }

    private Mono<Banner> handleImageUpdate(Banner banner, BannerUpdateRequest updateData) {
        String oldImageUrl = banner.getImageUrl();
        String newImageUrl = updateData.getImageUrl();

        if (newImageUrl == null) {
            return Mono.just(banner);
        }

        if (newImageUrl.startsWith("data:image/")) {
            return bannerStorageService.saveBanner(newImageUrl)
                    .flatMap(result -> {
                        banner.setImageUrl(result.originalPath());
                        return deleteOldImage(oldImageUrl).thenReturn(banner);
                    });
        }

        if (!newImageUrl.equals(oldImageUrl)) {
            banner.setImageUrl(newImageUrl);
            return deleteOldImage(oldImageUrl).thenReturn(banner);
        }

        return Mono.just(banner);
    }

    private Mono<Void> deleteOldImage(String oldImageUrl) {
        return Optional.ofNullable(oldImageUrl)
                .map(bannerStorageService::deleteBanner)
                .orElse(Mono.empty());
    }

    private Mono<Banner> updateBannerFields(Banner banner, BannerUpdateRequest updateData) {
        if (Boolean.TRUE.equals(updateData.getIsActive())) {
            banner.activate();
        } else {
            banner.deactivate();
        }

        banner.updateBanner(
                updateData.getTitle(),
                updateData.getType(),
                banner.getImageUrl(),
                updateData.getTargetUrl(),
                updateData.getDisplayOrder()
        );

        if (updateData.getStartDate() != null) {
            banner.setStartDate(updateData.getStartDate());
        }
        if (updateData.getEndDate() != null) {
            banner.setEndDate(updateData.getEndDate());
        }

        return Mono.just(banner);
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

    public record Request(String bannerId, BannerUpdateRequest updateData) {}
}
