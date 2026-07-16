package biz.ugur.busroutebackend.prediction.broadcast;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.prediction.v31")
public class V31BroadcastProperties {

    public enum Mode { OFF, SHADOW, LIVE }

    private Mode broadcast = Mode.OFF;

    private Set<String> routesAllowlist = Set.of();

    private double wBroadcastHz = 1.0;

    private int tHeartbeatSec = 5;

    private double pConfVarM2 = 2500.0;

    public boolean routeInScope(String routeNumber) {
        return routesAllowlist.isEmpty() || routesAllowlist.contains(routeNumber);
    }
}
