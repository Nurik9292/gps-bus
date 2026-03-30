package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the prediction cycle at a fixed rate (default 1000 ms).
 * Active only when {@code ugur.prediction.enabled=true} (the default).
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "ugur.prediction.enabled", havingValue = "true", matchIfMissing = true)
public class PositionPredictionScheduler {

    private final VehiclePositionPredictionService predictionService;

    public PositionPredictionScheduler(VehiclePositionPredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @Scheduled(fixedDelayString = "${ugur.prediction.interval-ms:1000}")
    public void runPredictionCycle() {
        predictionService.predictNextPositions()
                .subscribe(
                        null,
                        error -> log.error("Prediction cycle failed: {}", error.getMessage())
                );
    }
}
