package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.transport.domain.repository.SegmentLiveStateRepository;
import biz.ugur.busroutebackend.transport.domain.repository.SegmentTravelStatsRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentBaseline;
import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentLiveSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiveFactorShadowLoggerTest {

    @Mock
    private SegmentLiveStateRepository liveRepository;
    @Mock
    private SegmentTravelStatsRepository historyRepository;

    private biz.ugur.busroutebackend.transport.infrastructure.config.EtaLiveFactorProperties properties;
    private LiveFactorShadowLogger logger;
    private LiveFactorSnapshotHolder holder;

    private static final Instant NOW = Instant.parse("2026-07-18T05:30:00Z");

    @BeforeEach
    void setUp() {
        properties = new biz.ugur.busroutebackend.transport.infrastructure.config.EtaLiveFactorProperties();
        properties.setMode(
                biz.ugur.busroutebackend.transport.infrastructure.config.EtaLiveFactorProperties.Mode.SHADOW);
        org.springframework.beans.factory.ObjectProvider<biz.ugur.busroutebackend.prediction.shadow.V31ShadowService> noShadow =
                new org.springframework.beans.factory.ObjectProvider<>() {
                    @Override
                    public biz.ugur.busroutebackend.prediction.shadow.V31ShadowService getObject() {
                        throw new IllegalStateException("no bean");
                    }

                    @Override
                    public biz.ugur.busroutebackend.prediction.shadow.V31ShadowService getIfAvailable() {
                        return null;
                    }

                    @Override
                    public biz.ugur.busroutebackend.prediction.shadow.V31ShadowService getIfUnique() {
                        return null;
                    }

                    @Override
                    public biz.ugur.busroutebackend.prediction.shadow.V31ShadowService getObject(Object... args) {
                        throw new IllegalStateException("no bean");
                    }
                };
        holder = new LiveFactorSnapshotHolder();
        logger = new LiveFactorShadowLogger(liveRepository, historyRepository, properties,
                Clock.fixed(NOW, ZoneOffset.UTC), holder, noShadow);
    }

    @Test
    void computesClampedFactorsOnlyForEdgesWithEnoughSamples() {
        when(liveRepository.scanLiveEdges()).thenReturn(Flux.just(
                new SegmentLiveSnapshot("A", "B", 90.0, 5, NOW),
                new SegmentLiveSnapshot("B", "C", 40.0, 1, NOW),
                new SegmentLiveSnapshot("C", "D", 500.0, 4, NOW),
                new SegmentLiveSnapshot("D", "E", 30.0, 3, NOW)));
        when(historyRepository.findEdgeBaseline(eq("A"), eq("B"), anyInt(), anyBoolean()))
                .thenReturn(Mono.just(new SegmentBaseline("A", "B", 60.0, 20)));
        when(historyRepository.findEdgeBaseline(eq("C"), eq("D"), anyInt(), anyBoolean()))
                .thenReturn(Mono.just(new SegmentBaseline("C", "D", 100.0, 30)));
        when(historyRepository.findEdgeBaseline(eq("D"), eq("E"), anyInt(), anyBoolean()))
                .thenReturn(Mono.empty());

        StepVerifier.create(logger.collectFactors())
                .assertNext(factors -> {
                    assertThat(factors).hasSize(2);
                    LiveFactorShadowLogger.EdgeFactor ab = factors.stream()
                            .filter(f -> f.fromStopId().equals("A")).findFirst().orElseThrow();
                    assertThat(ab.factor()).isCloseTo(1.5, org.assertj.core.data.Offset.offset(0.01));
                    LiveFactorShadowLogger.EdgeFactor cd = factors.stream()
                            .filter(f -> f.fromStopId().equals("C")).findFirst().orElseThrow();
                    assertThat(cd.factor()).isEqualTo(3.0);
                })
                .verifyComplete();
    }

    @Test
    void baselineBelowMinSamplesIsSkipped() {
        when(liveRepository.scanLiveEdges()).thenReturn(Flux.just(
                new SegmentLiveSnapshot("A", "B", 90.0, 5, NOW)));
        when(historyRepository.findEdgeBaseline(eq("A"), eq("B"), anyInt(), anyBoolean()))
                .thenReturn(Mono.just(new SegmentBaseline("A", "B", 60.0, 2)));

        StepVerifier.create(logger.collectFactors())
                .assertNext(factors -> assertThat(factors).isEmpty())
                .verifyComplete();
    }

    @Test
    void shadowModePublishesEmptyFactors() {
        holder.publish(java.util.Map.of("X|Y", 2.0));
        reactor.test.StepVerifier.create(logger.applyIfLive(List.of(
                        new LiveFactorShadowLogger.EdgeFactor("A", "B", 90, 5, 60, 20, 1.5))))
                .expectNextCount(1)
                .verifyComplete();
        org.assertj.core.api.Assertions.assertThat(holder.size()).isZero();
    }

    @Test
    void liveModePublishesFactorsAndBuildsSnapshot() {
        properties.setMode(
                biz.ugur.busroutebackend.transport.infrastructure.config.EtaLiveFactorProperties.Mode.LIVE);
        when(historyRepository.findByHourAndWeekend(anyInt(), anyBoolean()))
                .thenReturn(reactor.core.publisher.Flux.empty());
        reactor.test.StepVerifier.create(logger.applyIfLive(List.of(
                        new LiveFactorShadowLogger.EdgeFactor("A", "B", 90, 5, 60, 20, 1.5))))
                .expectNextCount(1)
                .verifyComplete();
        org.assertj.core.api.Assertions.assertThat(holder.factor("A", "B")).isEqualTo(1.5);
        org.assertj.core.api.Assertions.assertThat(holder.factor("X", "Y")).isEqualTo(1.0);
    }

    @Test
    void liveModeAggregatesSharedEdgeAcrossRoutes() {
        properties.setMode(
                biz.ugur.busroutebackend.transport.infrastructure.config.EtaLiveFactorProperties.Mode.LIVE);
        biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat r34 =
                biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat
                        .initial("34", 0, "A", "B", 10, false).withNewSample(60.0, NOW);
        biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat r45 =
                biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat
                        .initial("45", 0, "A", "B", 10, false)
                        .withNewSample(120.0, NOW).withNewSample(120.0, NOW)
                        .withNewSample(120.0, NOW);
        when(historyRepository.findByHourAndWeekend(anyInt(), anyBoolean()))
                .thenReturn(reactor.core.publisher.Flux.just(r34, r45));

        reactor.test.StepVerifier.create(logger.applyIfLive(List.of()))
                .expectNextCount(1)
                .verifyComplete();

        var edge = holder.edgeBaseline("A", "B");
        org.assertj.core.api.Assertions.assertThat(edge).isNotNull();
        org.assertj.core.api.Assertions.assertThat(edge.n()).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(edge.meanSec())
                .isCloseTo(105.0, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void scanFailurePropagatesForTickErrorHandling() {
        when(liveRepository.scanLiveEdges())
                .thenReturn(Flux.error(new RuntimeException("redis down")));

        StepVerifier.create(logger.collectFactors())
                .expectErrorSatisfies(err ->
                        assertThat(err).hasMessageContaining("redis down"))
                .verify();
    }
}
