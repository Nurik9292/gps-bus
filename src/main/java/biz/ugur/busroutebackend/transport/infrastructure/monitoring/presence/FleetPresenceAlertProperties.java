package biz.ugur.busroutebackend.transport.infrastructure.monitoring.presence;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.fleet-presence-alerts")
public class FleetPresenceAlertProperties {

    private boolean enabled = false;
    private int checkIntervalMinutes = 10;
    private int silentThresholdMinutes = 15;
    private int startupGraceMinutes = 20;
    private int minResendCooldownMinutes = 60;
}
