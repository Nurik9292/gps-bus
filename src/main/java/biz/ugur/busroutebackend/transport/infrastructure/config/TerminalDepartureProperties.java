package biz.ugur.busroutebackend.transport.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.eta.terminal-departure")
public class TerminalDepartureProperties {

    public enum Mode { OFF, LIVE }

    private Mode mode = Mode.OFF;
    private int minSamples = 5;
    private int floorSeconds = 60;
    private int maxStops = 3;
    private long dwellMaxSeconds = 3600;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.OFF : mode;
    }

    public int getMinSamples() {
        return minSamples;
    }

    public void setMinSamples(int minSamples) {
        this.minSamples = minSamples;
    }

    public int getFloorSeconds() {
        return floorSeconds;
    }

    public void setFloorSeconds(int floorSeconds) {
        this.floorSeconds = floorSeconds;
    }

    public int getMaxStops() {
        return maxStops;
    }

    public void setMaxStops(int maxStops) {
        this.maxStops = maxStops;
    }

    public long getDwellMaxSeconds() {
        return dwellMaxSeconds;
    }

    public void setDwellMaxSeconds(long dwellMaxSeconds) {
        this.dwellMaxSeconds = dwellMaxSeconds;
    }
}
