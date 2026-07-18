package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LiveFactorSnapshotHolder {

    private final AtomicReference<Map<String, Double>> factorsByEdge =
            new AtomicReference<>(Map.of());

    public void publish(Map<String, Double> factors) {
        factorsByEdge.set(Map.copyOf(factors));
    }

    public double factor(String fromStopId, String toStopId) {
        Double f = factorsByEdge.get().get(fromStopId + "|" + toStopId);
        return f == null ? 1.0 : f;
    }

    public int size() {
        return factorsByEdge.get().size();
    }
}
