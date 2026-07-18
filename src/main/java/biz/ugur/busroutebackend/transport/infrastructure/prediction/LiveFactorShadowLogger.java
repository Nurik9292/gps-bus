package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.transport.domain.repository.SegmentLiveStateRepository;
import biz.ugur.busroutebackend.transport.domain.repository.SegmentTravelStatsRepository;
import biz.ugur.busroutebackend.transport.infrastructure.config.EtaLiveFactorProperties;
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
    private static final int EDGE_CONCURRENCY = 8;
    private static final int TOP_FACTORS_LOGGED = 5;

    private final SegmentLiveStateRepository liveRepository;
    private final SegmentTravelStatsRepository historyRepository;
    private final EtaLiveFactorProperties properties;
    private final Clock clock;

    private Disposable ticker;

    public LiveFactorShadowLogger(SegmentLiveStateRepository liveRepository,
                                  SegmentTravelStatsRepository historyRepository,
                                  EtaLiveFactorProperties properties,
                                  Clock clock) {
        this.liveRepository = liveRepository;
        this.historyRepository = historyRepository;
        this.properties = properties;
        this.clock = clock;
    }

    record EdgeFactor(String fromStopId, String toStopId,
                      double liveSeconds, long liveSamples,
                      double baseSeconds, long baseSamples, double factor) {
    }

    @PostConstruct
    void start() {
        if (properties.getMode() == EtaLiveFactorProperties.Mode.OFF) {
            log.info("[ETA_LIVE_FACTOR] режим OFF — shadow-логгер не запущен");
            return;
        }
        ticker = Flux.interval(TICK_PERIOD, TICK_PERIOD)
                .concatMap(t -> collectFactors()
                        .timeout(TICK_TIMEOUT)
                        .doOnNext(this::logSummary)
                        .onErrorResume(err -> {
                            log.warn("[ETA_LIVE_FACTOR] тик не удался: {}", err.getMessage());
                            return Mono.empty();
                        }))
                .subscribe(
                        summary -> {
                        },
                        err -> log.error("[ETA_LIVE_FACTOR] shadow-логгер остановлен: {}",
                                err.getMessage()));
        log.info("[ETA_LIVE_FACTOR] shadow-логгер запущен (режим {}, тик {} c)",
                properties.getMode(), TICK_PERIOD.toSeconds());
    }

    @PreDestroy
    void stop() {
        if (ticker != null) {
            ticker.dispose();
        }
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
