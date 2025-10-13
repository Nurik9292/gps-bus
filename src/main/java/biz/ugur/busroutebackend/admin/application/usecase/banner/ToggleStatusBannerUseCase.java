package biz.ugur.busroutebackend.admin.application.usecase.banner;

import biz.ugur.busroutebackend.admin.application.dto.banner.BannerResponse;
import biz.ugur.busroutebackend.admin.domain.model.Banner;
import biz.ugur.busroutebackend.admin.domain.repository.BannerRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Log4j2
@Service
public class ToggleStatusBannerUseCase extends BaseUseCase<Mono<ToggleStatusBannerUseCase.Request>, BannerResponse> {

    private static final String BANNER_NOT_FOUND_MSG = "Banner not found: ";

    private final BannerRepository bannerRepository;

    public ToggleStatusBannerUseCase(CorrelationContextService correlationContextService,
                                     EventBus eventBus,
                                     BannerRepository bannerRepository) {
        super(correlationContextService, eventBus);
        this.bannerRepository = bannerRepository;
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
                .flatMap(correlationId -> {
                    log.info("Changing banner status CorrelationId: {} - BannerId: {} - NewStatus: {}",
                            correlationId, request.id, request.active);

                    return bannerRepository.findById(BannerId.of(request.id))
                            .switchIfEmpty(Mono.error(() -> new IllegalArgumentException(BANNER_NOT_FOUND_MSG + request.id)))
                            .flatMap(banner -> updateBanner(banner, request.active))
                            .map(this::toResponse)
                            .doOnSuccess(b -> log.info("Banner status updated | BannerId={} | Active={}", request.id(), b.getIsActive()));
                });
    }

    private Mono<Banner> updateBanner(Banner banner, Boolean active) {
        if (Objects.requireNonNullElse(active, false)) {
            banner.activate();
        } else {
            banner.deactivate();
        }
        return bannerRepository.save(banner);
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

    public record Request(String id, Boolean active) {}
}
