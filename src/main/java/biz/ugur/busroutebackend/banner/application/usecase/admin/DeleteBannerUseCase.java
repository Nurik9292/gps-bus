package biz.ugur.busroutebackend.banner.application.usecase.admin;

import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.banner.domain.storage.BannerStorage;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteBannerUseCase extends BaseUseCase<Mono<String>, Void> {

    private final AdminBannerRepository bannerRepository;
    private final BannerStorage storage;


    public DeleteBannerUseCase(AdminBannerRepository bannerRepository,
                               BannerStorage storage,
                               CorrelationContextService  correlationContextService,
                               EventBus eventBus
                               ) {
        super(correlationContextService, eventBus);
        this.bannerRepository = bannerRepository;
        this.storage = storage;
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
                            storage.delete(banner.getImageUrl().getValue())
                                    .then(bannerRepository.deleteById(BannerId.of(bannerId)))
                    )
                    .doOnSuccess(v -> log.info("Banner deleted successfully: {}", bannerId))
                    .doOnError(error -> log.error("Failed to delete banner: {}", bannerId, error));
        });
    }
}