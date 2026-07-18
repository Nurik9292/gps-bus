package biz.ugur.busroutebackend.transport.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "app.eta.live-factor")
public class EtaLiveFactorProperties {

    private boolean writeEnabled = true;
    private List<String> excludedAxes = List.of();

    private volatile Set<String> excludedAxesSet = Set.of();

    public boolean isWriteEnabled() {
        return writeEnabled;
    }

    public void setWriteEnabled(boolean writeEnabled) {
        this.writeEnabled = writeEnabled;
    }

    public List<String> getExcludedAxes() {
        return excludedAxes;
    }

    public void setExcludedAxes(List<String> excludedAxes) {
        this.excludedAxes = excludedAxes == null ? List.of() : excludedAxes;
        this.excludedAxesSet = Set.copyOf(this.excludedAxes);
    }

    public boolean isAxisExcluded(String routeNumber, int direction) {
        return excludedAxesSet.contains(routeNumber + ":" + direction);
    }
}
