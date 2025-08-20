package biz.ugur.busroutebackend.admin.application.usecase.banner;

import biz.ugur.busroutebackend.admin.domain.repository.BannerRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.storage.BannerStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteBannerUseCase extends BaseUseCase<Mono<String>, Void> {

    private final BannerRepository bannerRepository;
    private final BannerStorageService bannerStorageService;


    public DeleteBannerUseCase(BannerRepository bannerRepository,
                               BannerStorageService bannerStorageService,
                               CorrelationContextService  correlationContextService,
                               EventBus eventBus
                               ) {
        super(correlationContextService, eventBus);
        this.bannerRepository = bannerRepository;
        this.bannerStorageService = bannerStorageService;
    }


    @Override
    protected Mono<Void> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<Void> processInternal(String bannerId) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Deleting banner CorrelationId: {} - BannerId: {}", correlationId, bannerId);

            return bannerRepository.findById(BannerId.of(bannerId))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Banner not found: " + bannerId)))
                    .flatMap(banner ->
                            bannerStorageService.deleteBanner(banner.getImageUrl())
                                    .then(bannerRepository.deleteById(BannerId.of(bannerId)))
                    )
                    .doOnSuccess(v -> log.info("Banner deleted successfully: {}", bannerId))
                    .doOnError(error -> log.error("Failed to delete banner: {}", bannerId, error));
        });
    }
}