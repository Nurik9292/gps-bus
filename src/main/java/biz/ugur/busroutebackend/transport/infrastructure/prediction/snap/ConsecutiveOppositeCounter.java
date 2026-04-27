package biz.ugur.busroutebackend.transport.infrastructure.prediction.snap;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConsecutiveOppositeCounter {

    private final ConcurrentHashMap<String, Integer> consecutive = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> pendingDirectionFixes = new ConcurrentHashMap<>();

    public int incrementAndGet(String vehicleId) {
        return consecutive.merge(vehicleId, 1, Integer::sum);
    }

    public void reset(String vehicleId) {
        consecutive.remove(vehicleId);
    }

    public void queueDirectionFix(String vehicleId, int direction) {
        pendingDirectionFixes.put(vehicleId, direction);
    }

    public Map<String, Integer> drainPendingDirectionFixes() {
        if (pendingDirectionFixes.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> result = new HashMap<>(pendingDirectionFixes);
        pendingDirectionFixes.clear();
        return result;
    }

    public void retainOnly(Set<String> livingVehicleIds) {
        consecutive.keySet().retainAll(livingVehicleIds);
    }
}
