package biz.ugur.busroutebackend.advertising.application.usecase.integration;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class WithdrawExternalBannerUseCase extends BaseUseCase<Mono<WithdrawExternalBannerUseCase.Command>, AdPlacement> {

    public record Command(String externalServiceId, String externalRef) {
    }

    private final AdPlacementRepository placementRepository;
    private final SecurityContextService securityService;

    public WithdrawExternalBannerUseCase(AdPlacementRepository placementRepository,
                                         SecurityContextService securityService,
                                         CorrelationContextService correlationService,
                                         EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.securityService = securityService;
    }

    @Override
    protected String getBoundContext() {
        return "advertising";
    }

    @Override
    protected Mono<AdPlacement> process(Mono<Command> request) {
        return request.flatMap(this::withdraw);
    }

    private static AdPlacement takeOffAir(AdPlacement placement) {
        return switch (placement.getStatus()) {
            case ACTIVE -> placement.markAsPaused();
            case DRAFT, PENDING_PAYMENT, SCHEDULED -> placement.cancel();
            case PAUSED, EXPIRED, CANCELLED -> placement;
        };
    }

    private Mono<AdPlacement> withdraw(Command command) {
        return placementRepository.findByExternalRef(command.externalServiceId(), command.externalRef())
                .switchIfEmpty(Mono.error(() -> new AdvertisingValidationException("externalRef",
                        "no external banner with reference " + command.externalRef())))
                .flatMap(placement -> {
                    if (!placement.isOwnedBy(command.externalServiceId())) {
                        return Mono.error(new AdvertisingValidationException("externalRef",
                                "placement belongs to another owner"));
                    }
                    return Mono.just(takeOffAir(placement));
                })
                .flatMap(placementRepository::save)
                .flatMap(saved -> {
                    log.info("[ExternalBanner] withdrawn service={} ref={} placement={}",
                            command.externalServiceId(), command.externalRef(), saved.getId().getValue());
                    return securityService.logAudit("EXTERNAL_BANNER_WITHDRAW",
                                    "placement:" + saved.getId().getValue(), command.externalServiceId())
                            .thenReturn(saved);
                });
    }
}
