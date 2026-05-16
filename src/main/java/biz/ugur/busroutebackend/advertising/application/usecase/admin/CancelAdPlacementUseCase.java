package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdPlacementNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CancelAdPlacementUseCase extends BaseUseCase<String, AdPlacementResponse> {

    private final AdPlacementRepository placementRepository;
    private final PaymentRepository paymentRepository;
    private final AdPlacementResponseMapper responseMapper;

    public CancelAdPlacementUseCase(AdPlacementRepository placementRepository,
                                     PaymentRepository paymentRepository,
                                     AdPlacementResponseMapper responseMapper,
                                     CorrelationContextService correlationService,
                                     EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.paymentRepository = paymentRepository;
        this.responseMapper = responseMapper;
    }

    @Override
    protected Mono<AdPlacementResponse> process(String placementId) {
        PlacementId id = PlacementId.of(placementId);
        return placementRepository.findById(id)
                .switchIfEmpty(Mono.error(new AdPlacementNotFoundException(placementId)))
                .map(p -> p.cancel())
                .flatMap(placementRepository::save)
                .flatMap(saved -> cancelPendingCashPayment(id).thenReturn(saved))
                .flatMap(responseMapper::toResponse)
                .doOnSuccess(r -> log.info("AdPlacement cancelled: id={}", r.id()));
    }

    private Mono<Void> cancelPendingCashPayment(PlacementId placementId) {
        return paymentRepository.findFirstBySubjectAndProviderAndStatus(
                        PaymentSubjectType.AD_PLACEMENT,
                        placementId.getValue(),
                        PaymentProvider.CASH,
                        PaymentStatus.REGISTERED)
                .flatMap(p -> paymentRepository.save(p.cancel("placement cancelled")))
                .then()
                .onErrorResume(err -> {
                    log.warn("Failed to cancel pending CASH payment for placement {}",
                            placementId.getValue(), err);
                    return Mono.empty();
                });
    }

    @Override
    protected String getBoundContext() { return "advertising.admin"; }
}
