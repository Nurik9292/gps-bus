package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.application.dto.banner.BannerCreateRequest;
import biz.ugur.busroutebackend.admin.application.dto.banner.BannerResponse;
import biz.ugur.busroutebackend.admin.domain.model.Banner;
import biz.ugur.busroutebackend.admin.domain.repository.BannerRepository;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateBannerUseCase implements UseCase<BannerCreateRequest, Mono<BannerResponse>> {

    private final BannerRepository bannerRepository;
    private final EventBus eventBus;

    public CreateBannerUseCase(BannerRepository bannerRepository, EventBus eventBus) {
        this.bannerRepository = bannerRepository;
        this.eventBus = eventBus;
    }

    @Override
    public Mono<BannerResponse> execute(BannerCreateRequest request) {
        log.info("Creating new banner: {}", request.getTitle());

        Banner banner = new Banner(
                request.getTitle(),
                request.getImageUrl(),
                request.getTargetUrl(),
                request.getDisplayOrder()
        );

        if (request.getEndDate() != null) {
            banner.setEndDate(request.getEndDate());
        }

        return bannerRepository.save(banner)
                .doOnNext(savedBanner -> {
                    savedBanner.getUncommittedEvents().forEach(eventBus::publish);
                    savedBanner.markEventsAsCommitted();
                })
                .map(this::toResponse)
                .doOnSuccess(response -> log.info("Banner created successfully: {}", response.getTitle()))
                .doOnError(error -> log.error("Failed to create banner: {}", request.getTitle(), error));
    }

    private BannerResponse toResponse(Banner banner) {
        return new BannerResponse(
                banner.getId().getValue(),
                banner.getTitle(),
                banner.getImageUrl(),
                banner.getTargetUrl(),
                banner.getIsActive(),
                banner.getDisplayOrder(),
                banner.getStartDate(),
                banner.getEndDate()
        );
    }
}