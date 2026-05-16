package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.repository.AdClickEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdImpressionEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAdvertisingDashboardOverviewUseCaseTest {

    @Mock private AdPlacementRepository placementRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private AdImpressionEventRepository impressionRepo;
    @Mock private AdClickEventRepository clickRepo;
    @Mock private CorrelationContextService correlationService;
    @Mock private EventBus eventBus;

    private GetAdvertisingDashboardOverviewUseCase useCase;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        useCase = new GetAdvertisingDashboardOverviewUseCase(
                placementRepository, paymentRepository, impressionRepo, clickRepo,
                clock, correlationService, eventBus);
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void happyPath_aggregatesAllFiveMetrics() {
        when(placementRepository.countByStatus(PlacementStatus.ACTIVE)).thenReturn(Mono.just(12L));
        when(paymentRepository.sumCompletedRevenueBetween(any(), any())).thenReturn(Mono.just(6200L));
        when(paymentRepository.countByProviderAndStatus(PaymentProvider.CASH, PaymentStatus.REGISTERED))
                .thenReturn(Mono.just(3L));
        when(impressionRepo.countByOccurredAtBetween(any(), any())).thenReturn(Mono.just(12340L));
        when(clickRepo.countByOccurredAtBetween(any(), any())).thenReturn(Mono.just(487L));

        StepVerifier.create(useCase.execute(null))
                .assertNext(overview -> {
                    assertThat(overview.windowDays()).isEqualTo(30);
                    assertThat(overview.activePlacements()).isEqualTo(12);
                    assertThat(overview.revenue30d()).isEqualTo(6200);
                    assertThat(overview.pendingCashCount()).isEqualTo(3);
                    assertThat(overview.impressions30d()).isEqualTo(12340);
                    assertThat(overview.clicks30d()).isEqualTo(487);
                    assertThat(overview.ctr30d()).isEqualByComparingTo(new BigDecimal("3.95"));
                }).verifyComplete();
    }

    @Test
    void zeroImpressions_ctrIsNull() {
        when(placementRepository.countByStatus(any())).thenReturn(Mono.just(0L));
        when(paymentRepository.sumCompletedRevenueBetween(any(), any())).thenReturn(Mono.just(0L));
        when(paymentRepository.countByProviderAndStatus(any(), any())).thenReturn(Mono.just(0L));
        when(impressionRepo.countByOccurredAtBetween(any(), any())).thenReturn(Mono.just(0L));
        when(clickRepo.countByOccurredAtBetween(any(), any())).thenReturn(Mono.just(0L));

        StepVerifier.create(useCase.execute(null))
                .assertNext(o -> assertThat(o.ctr30d()).isNull())
                .verifyComplete();
    }
}
