package biz.ugur.busroutebackend.transport.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "business.position-change")
public class PositionChangeProperties {

    private double positionDeltaThreshold = 0.00005;

    private double speedDeltaThresholdKmh = 2.0;

    private double minSpeedForMotionKmh = 3.0;
}
