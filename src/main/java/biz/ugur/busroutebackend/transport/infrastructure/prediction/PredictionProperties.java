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

    private boolean enabled = true;

    private int intervalMs = 1000;

    private int maxAgeMs = 10000;

    private double minSpeedKmh = 3.0;
    
    private double decayFactor = 0.98;

    private double correctionFactor = 0.7;

    private boolean snapToRoute = true;
}
