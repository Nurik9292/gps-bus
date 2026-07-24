package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TerminalDwellSnapshotHolder {

    public record DwellStat(double avgDwellSeconds, long sampleCount) {
    }

    private final AtomicReference<Map<String, DwellStat>> byRouteDirectionHour =
            new AtomicReference<>(Map.of());

    public void publish(Map<String, DwellStat> stats) {
        byRouteDirectionHour.set(Map.copyOf(stats));
    }

    public Optional<DwellStat> dwell(String routeId, int arrivedDirection, int arrivalHour) {
        return Optional.ofNullable(
                byRouteDirectionHour.get().get(key(routeId, arrivedDirection, arrivalHour)));
    }

    public static String key(String routeId, int arrivedDirection, int arrivalHour) {
        return routeId + "|" + arrivedDirection + "|" + arrivalHour;
    }

    public int size() {
        return byRouteDirectionHour.get().size();
    }
}
