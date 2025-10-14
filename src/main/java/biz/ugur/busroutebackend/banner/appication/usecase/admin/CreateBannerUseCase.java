package biz.ugur.busroutebackend.banner.appication.usecase.admin;

import biz.ugur.busroutebackend.banner.appication.dto.admin.BannerCreateRequest;
import biz.ugur.busroutebackend.banner.appication.dto.admin.BannerResponse;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.storage.BannerStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Service
@Slf4j
public class CreateBannerUseCase extends BaseUseCase<Mono<BannerCreateRequest>, BannerResponse> {

    private final AdminBannerRepository bannerRepository;
    private final BannerStorageService bannerStorageService;


    public CreateBannerUseCase(AdminBannerRepository bannerRepository,
                               CorrelationContextService correlationService,
                               EventBus eventBus,
                               BannerStorageService bannerStorageService) {
        super(correlationService, eventBus);
        this.bannerRepository = bannerRepository;
        this.bannerStorageService = bannerStorageService;
    }


    @Override
    protected Mono<BannerResponse> process(Mono<BannerCreateRequest> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<BannerResponse> processInternal(BannerCreateRequest request) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Creating new banner: CorrelationId - {} BannerTitle  {}", correlationId, request.getTitle());

            return processImageUrl(request.getImageUrl())
                    .flatMap(processedImageUrl -> {
                        Banner banner = Banner.create(
                                request.getTitle(),
                                BannerType.fromValue(request.getType()),
                                processedImageUrl,
                                request.getTargetUrl(),
                                request.getDisplayOrder(),
                                request.getContent()
                        );


                        if (request.getStartDate() != null) {
                            banner.setStartDate(request.getStartDate());
                        }

                        if (request.getEndDate() != null) {
                            banner.setEndDate(request.getEndDate());
                        }
                        log.info("startDate = {}, type = {}", banner.getStartDate(), banner.getStartDate().getClass());

                        return bannerRepository.save(banner).map(this::toResponse);
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