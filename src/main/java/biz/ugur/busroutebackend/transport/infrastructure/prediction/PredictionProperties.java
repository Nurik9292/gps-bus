package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ugur.prediction")
public class PredictionProperties {

    /** Enable/disable the entire prediction subsystem */
    private boolean enabled = true;

    /** How often the prediction scheduler fires, milliseconds */
    private int intervalMs = 1000;

    /** Max age of a GPS fix before its prediction is discarded, milliseconds */
    private int maxAgeMs = 10000;

    /** Minimum speed (km/h) below which dead reckoning is not applied */
    private double minSpeedKmh = 3.0;

    /** Per-cycle velocity decay factor (0.0–1.0). Applied to speedKmh each step. */
    private double decayFactor = 0.92;

    /**
     * Correction blend factor (0.0–1.0).
     * When a new real GPS fix arrives, the predicted position is blended toward
     * the real position: predicted += correctionFactor * (real - predicted).
     */
    private double correctionFactor = 0.7;

    /** Phase 2: snap predicted position to nearest route geometry segment */
    private boolean snapToRoute = false;
}
