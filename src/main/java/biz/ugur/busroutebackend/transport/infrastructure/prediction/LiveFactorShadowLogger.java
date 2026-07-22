package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.history.SegmentDwellHistory;
import biz.ugur.busroutebackend.prediction.shadow.V31ShadowService;
import biz.ugur.busroutebackend.transport.domain.repository.SegmentLiveStateRepository;
import biz.ugur.busroutebackend.transport.domain.repository.SegmentTravelStatsRepository;
import biz.ugur.busroutebackend.transport.infrastructure.config.EtaLiveFactorProperties;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class LiveFactorShadowLogger {

    private static final Logger log = LoggerFactory.getLogger(LiveFactorShadowLogger.class);
    private static final ZoneId ASHGABAT = ZoneId.of("Asia/Ashgabat");
    static final Duration TICK_PERIOD = Duration.ofSeconds(60);
    private static final Duration TICK_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration TERMINAL_DWELL_REFRESH_TIMEOUT = Duration.ofSeconds(10);
    private static final int EDGE_CONCURRENCY = 8;
    private static final int TOP_FACTORS_LOGGED = 5;

    private final SegmentLiveStateRepository liveRepository;
    private final SegmentTravelStatsRepository historyRepository;
    private final EtaLiveFactorProperties properties;
    private final Clock clock;
    private final LiveFactorSnapshotHolder snapshotHolder;
    private final ObjectProvider<V31ShadowService> shadowService;
    private final biz.ugur.busroutebackend.transport.domain.repository.TerminalDwellStatsRepository terminalDwellRepository;
    private final TerminalDwellSnapshotHolder terminalDwellSnapshotHolder;
    private final biz.ugur.busroutebackend.transport.infrastructure.config.TerminalDepartureProperties terminalDepartureProperties;

    private Disposable ticker;

    public LiveFactorShadowLogger(SegmentLiveStateRepository liveRepository,
                                  SegmentTravelStatsRepository historyRepository,
                                  EtaLiveFactorProperties properties,
                                  Clock clock,
                                  LiveFactorSnapshotHolder snapshotHolder,
                                  ObjectProvider<V31ShadowService> shadowService,
                                  biz.ugur.busroutebackend.transport.domain.repository.TerminalDwellStatsRepository terminalDwellRepository,
                                  TerminalDwellSnapshotHolder terminalDwellSnapshotHolder,
                                  biz.ugur.busroutebackend.transport.infrastructure.config.TerminalDepartureProperties terminalDepartureProperties) {
        this.liveRepository = liveRepository;
        this.historyRepository = historyRepository;
        this.properties = properties;
        this.clock = clock;
        this.snapshotHolder = snapshotHolder;
        this.shadowService = shadowService;
        this.terminalDwellRepository = terminalDwellRepository;
        this.terminalDwellSnapshotHolder = terminalDwellSnapshotHolder;
        this.terminalDepartureProperties = terminalDepartureProperties;
    }

    record EdgeFactor(String fromStopId, String toStopId,
                      double liveSeconds, long liveSamples,
                      double baseSeconds, long baseSamples, double factor) {
    }

    @PostConstruct
    void start() {
        if (properties.getMode() == EtaLiveFactorProperties.Mode.OFF && !terminalDwellsEnabled()) {
            log.info("[ETA_LIVE_FACTOR] режим OFF — shadow-логгер не запущен");
            return;
        }
        if (terminalDwellsEnabled() && !properties.isWriteEnabled()) {
            log.warn("[TERMINAL_DEPARTURE] режим live при выключенном сборе "
                    + "(ETA_LIVE_FACTOR_WRITE_ENABLED=false) — присутствие и отстои не пишутся");
        }
        ticker = tickerPipeline(Flux.interval(TICK_PERIOD, TICK_PERIOD))
                .subscribe(
                        summary -> {
                        },
                        err -> log.error("[ETA_LIVE_FACTOR] shadow-логгер остановлен: {}",
                                err.getMessage()));
        log.info("[ETA_LIVE_FACTOR] shadow-логгер запущен (режим {}, тик {} c)",
                properties.getMode(), TICK_PERIOD.toSeconds());
    }

    Flux<List<EdgeFactor>> tickerPipeline(Flux<Long> ticks) {
        return ticks
                .onBackpressureDrop(tick -> log.warn(
                        "[ETA_LIVE_FACTOR] тик {} пропущен — предыдущий ещё выполняется", tick))
                .concatMap(t -> wholeTickWithTimeout(), 1);
    }

    private Mono<List<EdgeFactor>> wholeTickWithTimeout() {
        Mono<List<EdgeFactor>> factorsPart = properties.getMode() == EtaLiveFactorProperties.Mode.OFF
                ? Mono.just(List.of())
                : collectFactors().doOnNext(this::logSummary).flatMap(this::applyIfLive);
        return isolatedTerminalDwellsRefresh()
                .then(factorsPart)
                .timeout(TICK_TIMEOUT)
                .onErrorResume(err -> {
                    log.warn("[ETA_LIVE_FACTOR] тик не удался: {}", err.getMessage());
                    return Mono.empty();
                });
    }

    private boolean terminalDwellsEnabled() {
        return terminalDepartureProperties.getMode()
                == biz.ugur.busroutebackend.transport.infrastructure.config.TerminalDepartureProperties.Mode.LIVE;
    }

    private Mono<Void> isolatedTerminalDwellsRefresh() {
        if (!terminalDwellsEnabled()) {
            return Mono.empty();
        }
        return publishTerminalDwells()
                .timeout(TERMINAL_DWELL_REFRESH_TIMEOUT)
                .onErrorResume(err -> {
                    log.warn("[TERMINAL_DEPARTURE] обновление отстоев не удалось: {}", err.getMessage());
                    return Mono.empty();
                });
    }

    @PreDestroy
    void stop() {
        if (ticker != null) {
            ticker.dispose();
        }
    }

    Mono<Void> publishTerminalDwells() {
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), ASHGABAT);
        ZonedDateTime prevHour = now.minusHours(1);
        return Flux.concat(
                        dwellStatsOfHour(prevHour.getHour(), prevHour.getDayOfWeek().getValue() >= 6),
                        dwellStatsOfHour(now.getHour(), now.getDayOfWeek().getValue() >= 6))
                .collectMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue,
                        java.util.HashMap::new)
                .doOnNext(terminalDwellSnapshotHolder::publish)
                .then();
    }

    private Flux<java.util.Map.Entry<String, TerminalDwellSnapshotHolder.DwellStat>> dwellStatsOfHour(
            int hourOfDay, boolean weekend) {
        return terminalDwellRepository.findByHourAndWeekend(hourOfDay, weekend)
                .map(stat -> java.util.Map.entry(
                        TerminalDwellSnapshotHolder.key(stat.getRouteNumber(), stat.getDirection(),
                                stat.getHourOfDay()),
                        new TerminalDwellSnapshotHolder.DwellStat(
                                stat.getAvgDwellSeconds(), stat.getSampleCount())));
    }

    Mono<List<EdgeFactor>> collectFactors() {
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), ASHGABAT);
        int hour = now.getHour();
        boolean weekend = now.getDayOfWeek().getValue() >= 6;
        return liveRepository.scanLiveEdges()
                .filter(snap -> snap.sampleCount() >= properties.getMinLiveSamples())
                .flatMap(snap -> historyRepository
                                .findEdgeBaseline(snap.fromStopId(), snap.toStopId(), hour, weekend)
                                .filter(base -> base.totalSamples() >= properties.getMinBaselineSamples()
                                        && base.weightedAvgSeconds() > 0)
                                .map(base -> new EdgeFactor(
                                        snap.fromStopId(), snap.toStopId(),
                                        snap.emaSeconds(), snap.sampleCount(),
                                        base.weightedAvgSeconds(), base.totalSamples(),
                                        clamp(snap.emaSeconds() / base.weightedAvgSeconds()))),
                        EDGE_CONCURRENCY)
                .collectList();
    }

    Mono<List<EdgeFactor>> applyIfLive(List<EdgeFactor> factors) {
        if (properties.getMode() != EtaLiveFactorProperties.Mode.LIVE) {
            snapshotHolder.publish(java.util.Map.of());
            return Mono.just(factors);
        }
        java.util.Map<String, Double> byEdge = new java.util.HashMap<>();
        for (EdgeFactor f : factors) {
            byEdge.put(SegmentDwellHistory.edgeKey(f.fromStopId(), f.toStopId()), f.factor());
        }
        snapshotHolder.publish(byEdge);
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), ASHGABAT);
        int hour = now.getHour();
        boolean weekend = now.getDayOfWeek().getValue() >= 6;
        return historyRepository.findByHourAndWeekend(hour, weekend)
                .collectMultimap(stat -> SegmentDwellHistory.edgeKey(
                        stat.getFromStopId(), stat.getToStopId()))
                .map(byEdgeRows -> {
                    java.util.Map<String, SegmentDwellHistory.Stat> aggregated =
                            new java.util.HashMap<>();
                    byEdgeRows.forEach((edge, rows) -> {
                        double weighted = 0;
                        long samples = 0;
                        for (var stat : rows) {
                            weighted += stat.getAvgTravelSeconds() * stat.getSampleCount();
                            samples += stat.getSampleCount();
                        }
                        if (samples > 0) {
                            aggregated.put(edge, new SegmentDwellHistory.Stat(
                                    weighted / samples, (int) samples));
                        }
                    });
                    return aggregated;
                })
                .doOnNext(snapshotHolder::publishBaselines)
                .map(aggregated -> {
                    java.util.Map<String, SegmentDwellHistory.Stat> seg = new java.util.HashMap<>();
                    aggregated.forEach((edge, stat) -> {
                        int sep = edge.indexOf('|');
                        seg.put(SegmentDwellHistory.segKey(edge.substring(0, sep),
                                edge.substring(sep + 1), hour, weekend), stat);
                    });
                    return new SegmentDwellHistory(java.util.Map.of(), seg, byEdge);
                })
                .doOnNext(this::distributeToCores)
                .thenReturn(factors);
    }

    private void distributeToCores(SegmentDwellHistory snapshot) {
        V31ShadowService shadow = shadowService.getIfAvailable();
        if (shadow == null) {
            return;
        }
        int n = 0;
        for (MotionFilterCore core : shadow.coresView().values()) {
            synchronized (core) {
                core.withHistory(snapshot);
            }
            n++;
        }
        log.debug("[ETA_LIVE_FACTOR] снапшот роздан {} ядрам (сегментов={}, факторов={})",
                n, snapshot.segTravelByKey().size(), snapshot.liveFactorByEdge().size());
    }

    private double clamp(double factor) {
        return Math.max(properties.getFactorFloor(),
                Math.min(properties.getFactorCeiling(), factor));
    }

    private void logSummary(List<EdgeFactor> factors) {
        if (factors.isEmpty()) {
            log.info("[ETA_LIVE_FACTOR] рёбер с фактором: 0 (мало live/base семплов)");
            return;
        }
        List<Double> sorted = new ArrayList<>(factors.stream().map(EdgeFactor::factor).sorted().toList());
        double median = sorted.get(sorted.size() / 2);
        double p90 = sorted.get((int) Math.min(sorted.size() - 1L, Math.round(sorted.size() * 0.9)));
        log.info("[ETA_LIVE_FACTOR] рёбер={} median={} p90={} floor..ceil=[{}..{}]",
                factors.size(),
                String.format(Locale.ROOT, "%.2f", median),
                String.format(Locale.ROOT, "%.2f", p90),
                properties.getFactorFloor(), properties.getFactorCeiling());
        factors.stream()
                .sorted((a, b) -> Double.compare(
                        Math.abs(b.factor() - 1.0), Math.abs(a.factor() - 1.0)))
                .limit(TOP_FACTORS_LOGGED)
                .forEach(f -> log.info(
                        "[ETA_LIVE_FACTOR] edge={}->{} live={}s(n={}) base={}s(n={}) factor={}",
                        f.fromStopId(), f.toStopId(),
                        String.format(Locale.ROOT, "%.1f", f.liveSeconds()), f.liveSamples(),
                        String.format(Locale.ROOT, "%.1f", f.baseSeconds()), f.baseSamples(),
                        String.format(Locale.ROOT, "%.2f", f.factor())));
    }
}
