package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdPlacementNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdClickEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdDetailViewEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdImpressionEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.DetailViewSummary;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAdPlacementAnalyticsUseCaseTest {

    @Mock private AdPlacementRepository placementRepository;
    @Mock private AdImpressionEventRepository impressionRepo;
    @Mock private AdClickEventRepository clickRepo;
    @Mock private AdDetailViewEventRepository detailViewRepo;
    @Mock private CorrelationContextService correlationService;
    @Mock private EventBus eventBus;

    private GetAdPlacementAnalyticsUseCase useCase;

    private final Instant now = Instant.parse("2026-05-14T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        useCase = new GetAdPlacementAnalyticsUseCase(
                placementRepository, impressionRepo, clickRepo, detailViewRepo,
                clock, correlationService, eventBus);
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void happyPath_computesCtr_andReturnsAggregates() {
        PlacementId pid = PlacementId.of(UUID.randomUUID().toString());
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to   = Instant.parse("2026-05-14T00:00:00Z");

        AdPlacement placement = mock(AdPlacement.class);
        when(placementRepository.findById(pid)).thenReturn(Mono.just(placement));

        when(impressionRepo.countByPlacementIdAndOccurredAtBetween(pid, from, to)).thenReturn(Mono.just(1000L));
        when(clickRepo.countByPlacementIdAndOccurredAtBetween(pid, from, to)).thenReturn(Mono.just(50L));
        when(detailViewRepo.summarizeByPlacementIdAndOccurredAtBetween(pid, from, to))
                .thenReturn(Mono.just(new DetailViewSummary(10L, 8400L)));

        StepVerifier.create(useCase.execute(new GetAdPlacementAnalyticsUseCase.Query(pid, from, to)))
                .assertNext(resp -> {
                    assertThat(resp.impressions()).isEqualTo(1000);
                    assertThat(resp.clicks()).isEqualTo(50);
                    assertThat(resp.ctr()).isEqualByComparingTo(new BigDecimal("5.00"));
                    assertThat(resp.detailViews()).isEqualTo(10);
                    assertThat(resp.avgDwellMs()).isEqualTo(8400L);
                })
                .verifyComplete();
    }

    @Test
    void zeroImpressions_ctrIsNull() {
        PlacementId pid = PlacementId.of(UUID.randomUUID().toString());
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to   = Instant.parse("2026-05-14T00:00:00Z");

        AdPlacement placement = mock(AdPlacement.class);
        when(placementRepository.findById(pid)).thenReturn(Mono.just(placement));
        when(impressionRepo.countByPlacementIdAndOccurredAtBetween(pid, from, to)).thenReturn(Mono.just(0L));
        when(clickRepo.countByPlacementIdAndOccurredAtBetween(pid, from, to)).thenReturn(Mono.just(0L));
        when(detailViewRepo.summarizeByPlacementIdAndOccurredAtBetween(pid, from, to))
                .thenReturn(Mono.just(new DetailViewSummary(0L, null)));

        StepVerifier.create(useCase.execute(new GetAdPlacementAnalyticsUseCase.Query(pid, from, to)))
                .assertNext(resp -> {
                    assertThat(resp.ctr()).isNull();
                    assertThat(resp.avgDwellMs()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void placementNotFound_returns404Exception() {
        PlacementId pid = PlacementId.of(UUID.randomUUID().toString());
        when(placementRepository.findById(pid)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new GetAdPlacementAnalyticsUseCase.Query(pid, null, null)))
                .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AdPlacementNotFoundException.class))
                .verify();
    }

    @Test
    void fromAfterTo_throwsIllegalArgument() {
        PlacementId pid = PlacementId.of(UUID.randomUUID().toString());
        AdPlacement placement = mock(AdPlacement.class);
        when(placementRepository.findById(pid)).thenReturn(Mono.just(placement));

        Instant from = Instant.parse("2026-05-14T00:00:00Z");
        Instant to   = Instant.parse("2026-05-01T00:00:00Z");

        StepVerifier.create(useCase.execute(new GetAdPlacementAnalyticsUseCase.Query(pid, from, to)))
                .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(IllegalArgumentException.class))
                .verify();
    }

    @Test
    void periodOverOneYear_throwsIllegalArgument() {
        PlacementId pid = PlacementId.of(UUID.randomUUID().toString());
        AdPlacement placement = mock(AdPlacement.class);
        when(placementRepository.findById(pid)).thenReturn(Mono.just(placement));

        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to   = Instant.parse("2026-01-02T00:00:00Z");

        StepVerifier.create(useCase.execute(new GetAdPlacementAnalyticsUseCase.Query(pid, from, to)))
                .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(IllegalArgumentException.class))
                .verify();
    }
}
