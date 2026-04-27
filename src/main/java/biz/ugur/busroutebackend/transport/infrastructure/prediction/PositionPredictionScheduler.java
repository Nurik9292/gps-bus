package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
@ConditionalOnProperty(name = "ugur.prediction.enabled", havingValue = "true", matchIfMissing = true)
public class PositionPredictionScheduler {

    private static final Duration CYCLE_TIMEOUT = Duration.ofSeconds(5);
    private static final long HEARTBEAT_LOG_EVERY_N_CYCLES = 30;

    private final VehiclePositionPredictionService predictionService;
    private final AtomicLong cycleCounter = new AtomicLong(0);

    public PositionPredictionScheduler(VehiclePositionPredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @Scheduled(fixedDelayString = "${ugur.prediction.interval-ms:1000}")
    public void runPredictionCycle() {
        long cycle = cycleCounter.incrementAndGet();
        Instant cycleStart = Instant.now();

        predictionService.predictNextPositions()
                .timeout(CYCLE_TIMEOUT)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        error -> {
                            if (error instanceof TimeoutException) {
                                log.warn("[GPS_PIPELINE] PRED_CYCLE_TIMEOUT cycle={} exceeded {}s, skipping",
                                        cycle, CYCLE_TIMEOUT.toSeconds());
                            } else {
                                log.error("[GPS_PIPELINE] PRED_CYCLE_FAILED cycle={}", cycle, error);
                            }
                        },
                        () -> {
                            long durationMs = Instant.now().toEpochMilli() - cycleStart.toEpochMilli();
                            if (cycle % HEARTBEAT_LOG_EVERY_N_CYCLES == 0) {
                                log.info("[GPS_PIPELINE] PRED_CYCLE_HEARTBEAT cycle={} durationMs={}",
                                        cycle, durationMs);
                            } else if (durationMs > CYCLE_TIMEOUT.toMillis() / 2) {
                                log.warn("[GPS_PIPELINE] PRED_CYCLE_SLOW cycle={} durationMs={} (>{}ms threshold)",
                                        cycle, durationMs, CYCLE_TIMEOUT.toMillis() / 2);
                            }
                        }
                );
    }
}
