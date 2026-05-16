package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdvertisingDashboardOverview;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.repository.AdClickEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdImpressionEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class GetAdvertisingDashboardOverviewUseCase
        extends BaseUseCase<Void, AdvertisingDashboardOverview> {

    private static final int WINDOW_DAYS = 30;
    private static final String CURRENCY = "TMT";

    private final AdPlacementRepository placementRepository;
    private final PaymentRepository paymentRepository;
    private final AdImpressionEventRepository impressionRepo;
    private final AdClickEventRepository clickRepo;
    private final Clock clock;

    public GetAdvertisingDashboardOverviewUseCase(AdPlacementRepository placementRepository,
                                                  PaymentRepository paymentRepository,
                                                  AdImpressionEventRepository impressionRepo,
                                                  AdClickEventRepository clickRepo,
                                                  Clock clock,
                                                  CorrelationContextService correlationService,
                                                  EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.paymentRepository = paymentRepository;
        this.impressionRepo = impressionRepo;
        this.clickRepo = clickRepo;
        this.clock = clock;
    }

    @Override
    protected Mono<AdvertisingDashboardOverview> process(Void unused) {
        Instant now = clock.instant();
        Instant since = now.minus(Duration.ofDays(WINDOW_DAYS));
        return Mono.zip(
                placementRepository.countByStatus(PlacementStatus.ACTIVE),
                paymentRepository.sumCompletedRevenueBetween(since, now),
                paymentRepository.countByProviderAndStatus(PaymentProvider.CASH, PaymentStatus.REGISTERED),
                impressionRepo.countByOccurredAtBetween(since, now),
                clickRepo.countByOccurredAtBetween(since, now)
        ).map(t -> AdvertisingDashboardOverview.of(
                WINDOW_DAYS, t.getT1(), t.getT2(), CURRENCY,
                t.getT3(), t.getT4(), t.getT5()));
    }

    @Override
    protected String getBoundContext() {
        return "advertising.admin";
    }
}
