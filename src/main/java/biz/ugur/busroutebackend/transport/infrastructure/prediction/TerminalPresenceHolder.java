package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TerminalPresenceHolder {

    public record TerminalPresence(String routeNumber, int arrivedDirection, Instant arrivedAt) {
    }

    private final Map<String, TerminalPresence> byVehicleId = new ConcurrentHashMap<>();

    public void arrived(String vehicleId, String routeNumber, int arrivedDirection, Instant arrivedAt) {
        byVehicleId.put(vehicleId, new TerminalPresence(routeNumber, arrivedDirection, arrivedAt));
    }

    public void departed(String vehicleId) {
        byVehicleId.remove(vehicleId);
    }

    public Optional<TerminalPresence> presentAt(String vehicleId, Instant now, long maxDwellSeconds) {
        TerminalPresence presence = byVehicleId.get(vehicleId);
        if (presence == null) {
            return Optional.empty();
        }
        long elapsed = now.getEpochSecond() - presence.arrivedAt().getEpochSecond();
        if (elapsed < 0 || elapsed > maxDwellSeconds) {
            return Optional.empty();
        }
        return Optional.of(presence);
    }

    public Map<String, TerminalPresence> snapshotFresh(Instant now, long maxDwellSeconds) {
        Map<String, TerminalPresence> fresh = new ConcurrentHashMap<>();
        byVehicleId.forEach((vehicleId, presence) -> {
            long elapsed = now.getEpochSecond() - presence.arrivedAt().getEpochSecond();
            if (elapsed >= 0 && elapsed <= maxDwellSeconds) {
                fresh.put(vehicleId, presence);
            }
        });
        return fresh;
    }

    public void retainVehicles(Set<String> activeVehicleIds) {
        byVehicleId.keySet().retainAll(activeVehicleIds);
    }

    public int size() {
        return byVehicleId.size();
    }
}
