package biz.ugur.busroutebackend.transport.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "app.eta.live-factor")
public class EtaLiveFactorProperties {

    public enum Mode { OFF, SHADOW, LIVE }

    private boolean writeEnabled = true;
    private Mode mode = Mode.OFF;
    private double factorFloor = 0.5;
    private double factorCeiling = 3.0;
    private int minLiveSamples = 2;
    private int minBaselineSamples = 3;
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

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.OFF : mode;
    }

    public double getFactorFloor() {
        return factorFloor;
    }

    public void setFactorFloor(double factorFloor) {
        this.factorFloor = factorFloor;
    }

    public double getFactorCeiling() {
        return factorCeiling;
    }

    public void setFactorCeiling(double factorCeiling) {
        this.factorCeiling = factorCeiling;
    }

    public int getMinLiveSamples() {
        return minLiveSamples;
    }

    public void setMinLiveSamples(int minLiveSamples) {
        this.minLiveSamples = minLiveSamples;
    }

    public int getMinBaselineSamples() {
        return minBaselineSamples;
    }

    public void setMinBaselineSamples(int minBaselineSamples) {
        this.minBaselineSamples = minBaselineSamples;
    }

    public boolean isAxisExcluded(String routeNumber, int direction) {
        return excludedAxesSet.contains(routeNumber + ":" + direction);
    }
}
