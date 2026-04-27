package biz.ugur.busroutebackend.transport.infrastructure.prediction.snap;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.PredictionProperties;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class DirectionChangeCooldown {

    private final PredictionProperties properties;

    public DirectionChangeCooldown(PredictionProperties properties) {
        this.properties = properties;
    }

    public boolean isActive(VehiclePredictionState existing) {
        if (existing == null || existing.getDirectionChangedAt() == null) {
            return false;
        }
        long ageMs = Duration.between(existing.getDirectionChangedAt(), Instant.now()).toMillis();
        return ageMs >= 0 && ageMs < properties.getDirChangeCooldownMs();
    }

    public long ageMs(VehiclePredictionState existing) {
        if (existing == null || existing.getDirectionChangedAt() == null) {
            return -1;
        }
        return Duration.between(existing.getDirectionChangedAt(), Instant.now()).toMillis();
    }
}
