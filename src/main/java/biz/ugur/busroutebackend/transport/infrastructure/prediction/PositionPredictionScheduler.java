package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
@ConditionalOnProperty(name = "ugur.prediction.enabled", havingValue = "true", matchIfMissing = true)
public class PositionPredictionScheduler {

    private static final Duration CYCLE_TIMEOUT = Duration.ofSeconds(5);

    private final VehiclePositionPredictionService predictionService;

    public PositionPredictionScheduler(VehiclePositionPredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @Scheduled(fixedDelayString = "${ugur.prediction.interval-ms:1000}")
    public void runPredictionCycle() {
        predictionService.predictNextPositions()
                .timeout(CYCLE_TIMEOUT)
                .subscribe(
                        null,
                        error -> {
                            if (error instanceof TimeoutException) {
                                log.warn("[GPS_PIPELINE] PRED_CYCLE_TIMEOUT exceeded {}s, skipping",
                                        CYCLE_TIMEOUT.toSeconds());
                            } else {
                                log.error("[GPS_PIPELINE] Prediction cycle failed", error);
                            }
                        }
                );
    }
}
