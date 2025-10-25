package biz.ugur.busroutebackend.banner.appication.usecase.admin;

import biz.ugur.busroutebackend.banner.appication.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.appication.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
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

    private final AdminBannerRepository bannerRepository;
    private final BannerResponseMapper bannerResponseMapper;

    public ToggleStatusBannerUseCase(CorrelationContextService correlationContextService,
                                     EventBus eventBus,
                                     AdminBannerRepository bannerRepository,
                                     BannerResponseMapper bannerResponseMapper) {
        super(correlationContextService, eventBus);
        this.bannerRepository = bannerRepository;
        this.bannerResponseMapper = bannerResponseMapper;
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
                            .flatMap(bannerResponseMapper::toResponse)
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

    public record Request(String id, Boolean active) {}
}
