package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdPlacementNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdClickEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdDetailViewEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdImpressionEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAdPlacementAnalyticsTrendUseCaseTest {

    @Mock private AdPlacementRepository placementRepository;
    @Mock private AdImpressionEventRepository impressionRepo;
    @Mock private AdClickEventRepository clickRepo;
    @Mock private AdDetailViewEventRepository detailViewRepo;
    @Mock private CorrelationContextService correlationService;
    @Mock private EventBus eventBus;

    private GetAdPlacementAnalyticsTrendUseCase useCase;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        useCase = new GetAdPlacementAnalyticsTrendUseCase(
                placementRepository, impressionRepo, clickRepo, detailViewRepo,
                clock, correlationService, eventBus);
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void happyPath_returns3DaySeriesWithGapFill() {
        PlacementId pid = PlacementId.of(UUID.randomUUID().toString());
        Instant from = Instant.parse("2026-05-10T00:00:00Z");
        Instant to   = Instant.parse("2026-05-13T00:00:00Z");

        AdPlacement placement = mock(AdPlacement.class);
        when(placementRepository.findById(pid)).thenReturn(Mono.just(placement));
        when(impressionRepo.countByDayBetween(pid, from, to))
                .thenReturn(Mono.just(Map.of(
                        LocalDate.parse("2026-05-10"), 100L,
                        LocalDate.parse("2026-05-12"), 50L)));
        when(clickRepo.countByDayBetween(pid, from, to))
                .thenReturn(Mono.just(Map.of(LocalDate.parse("2026-05-10"), 5L)));
        when(detailViewRepo.summarizeByDayBetween(pid, from, to))
                .thenReturn(Mono.just(Map.of()));

        StepVerifier.create(useCase.execute(new GetAdPlacementAnalyticsTrendUseCase.Query(pid, from, to)))
                .assertNext(resp -> {
                    assertThat(resp.points()).hasSize(4);
                    assertThat(resp.points().get(0).day()).isEqualTo(LocalDate.parse("2026-05-10"));
                    assertThat(resp.points().get(0).impressions()).isEqualTo(100L);
                    assertThat(resp.points().get(0).clicks()).isEqualTo(5L);
                    assertThat(resp.points().get(1).impressions()).isEqualTo(0L);
                    assertThat(resp.points().get(1).ctr()).isNull();
                    assertThat(resp.points().get(2).impressions()).isEqualTo(50L);
                    assertThat(resp.points().get(3).impressions()).isEqualTo(0L);
                })
                .verifyComplete();
    }

    @Test
    void placementNotFound_returns404Exception() {
        PlacementId pid = PlacementId.of(UUID.randomUUID().toString());
        when(placementRepository.findById(pid)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new GetAdPlacementAnalyticsTrendUseCase.Query(pid, null, null)))
                .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AdPlacementNotFoundException.class))
                .verify();
    }

    @Test
    void periodOverOneYear_throwsIllegalArgument() {
        PlacementId pid = PlacementId.of(UUID.randomUUID().toString());
        AdPlacement placement = mock(AdPlacement.class);
        when(placementRepository.findById(pid)).thenReturn(Mono.just(placement));

        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to   = Instant.parse("2026-01-02T00:00:00Z");

        StepVerifier.create(useCase.execute(new GetAdPlacementAnalyticsTrendUseCase.Query(pid, from, to)))
                .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(IllegalArgumentException.class))
                .verify();
    }
}
