package biz.ugur.busroutebackend.admin.application.usecase.banner;

import biz.ugur.busroutebackend.admin.application.dto.banner.BannerCreateRequest;
import biz.ugur.busroutebackend.admin.application.dto.banner.BannerResponse;
import biz.ugur.busroutebackend.admin.domain.model.Banner;
import biz.ugur.busroutebackend.admin.domain.repository.BannerRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.shared.infrastructure.storage.BannerStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Service
@Slf4j
public class CreateBannerUseCase implements UseCase<Mono<BannerCreateRequest>, Mono<BannerResponse>> {

    private final BannerRepository bannerRepository;
    private final CorrelationContextService correlationService;
    private final EventBus eventBus;
    private final BannerStorageService bannerStorageService;


    public CreateBannerUseCase(BannerRepository bannerRepository,
                               CorrelationContextService correlationService,
                               EventBus eventBus,
                               BannerStorageService bannerStorageService) {
        this.bannerRepository = bannerRepository;
        this.correlationService = correlationService;
        this.eventBus = eventBus;
        this.bannerStorageService = bannerStorageService;
    }

    @Override
    public Mono<BannerResponse> execute(Mono<BannerCreateRequest> request) {
        return correlationService.executeWithCorrelation(request.flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<BannerResponse> executeWithCorrelation(BannerCreateRequest request) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Creating new banner: CorrelationId - {} BannerTitle  {}", correlationId, request.getTitle());


            return processImageUrl(request.getImageUrl())
                    .flatMap(processedImageUrl -> {
                        Banner banner = new Banner(
                                request.getTitle(),
                                request.getType(),
                                processedImageUrl,
                                request.getTargetUrl(),
                                request.getDisplayOrder()
                        );

                        if (request.getStartDate() != null) {
                            banner.setStartDate(request.getStartDate());
                        }

                        if (request.getEndDate() != null) {
                            banner.setEndDate(request.getEndDate());
                        }

                        return bannerRepository.save(banner)
                                .doOnNext(savedBanner -> {
                                    savedBanner.getUncommittedEvents().forEach(eventBus::publish);
                                    savedBanner.markEventsAsCommitted();
                                })
                                .map(this::toResponse);
                    })
                    .doOnSuccess(response -> log.info("Banner created successfully: {}", response.getTitle()))
                    .doOnError(error -> log.error("Failed to create banner: {}", request.getTitle(), error));
        });
    }

    private Mono<String> processImageUrl(String imageUrl) {
        if (imageUrl != null && imageUrl.startsWith("data:image/")) {
            return bannerStorageService.saveBanner(imageUrl)
                    .map(BannerStorageService.BannerResult::getDisplayUrl);
        }

        return Mono.just(Objects.requireNonNull(imageUrl));
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