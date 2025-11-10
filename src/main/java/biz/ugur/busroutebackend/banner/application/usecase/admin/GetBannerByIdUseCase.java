package biz.ugur.busroutebackend.banner.application.usecase.admin;

import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.application.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerNotFoundException;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetBannerByIdUseCase extends BaseUseCase<Mono<String>, BannerResponse> {

    private final AdminBannerRepository bannerRepository;
    private final BannerResponseMapper bannerResponseMapper;

    public GetBannerByIdUseCase(AdminBannerRepository bannerRepository,
                                CorrelationContextService correlationService,
                                EventBus eventBus,
                                BannerResponseMapper bannerResponseMapper) {
        super(correlationService, eventBus);
        this.bannerRepository = bannerRepository;
        this.bannerResponseMapper = bannerResponseMapper;
    }

    @Override
    protected Mono<BannerResponse> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<BannerResponse> processInternal(String bannerId) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Fetching banner by ID - CorrelationId: {}, BannerId: {}", correlationId, bannerId);

            return bannerRepository.findById(BannerId.of(bannerId))
                    .switchIfEmpty(Mono.error(new BannerNotFoundException(bannerId)))
                    .flatMap(bannerResponseMapper::toResponse)
                    .doOnSuccess(response -> log.debug(
                            "CorrelationId: {} - Successfully retrieved banner with id: {}, title: {}",
                            correlationId,
                            bannerId,
                            response.title()
                    ));
        });
    }
}
