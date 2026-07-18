package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.prediction.core.history.SegmentDwellHistory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LiveFactorSnapshotHolder {

    private final AtomicReference<Map<String, Double>> factorsByEdge =
            new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, SegmentDwellHistory.Stat>> edgeBaselines =
            new AtomicReference<>(Map.of());

    public void publish(Map<String, Double> factors) {
        factorsByEdge.set(Map.copyOf(factors));
    }

    public void publishBaselines(Map<String, SegmentDwellHistory.Stat> baselines) {
        edgeBaselines.set(Map.copyOf(baselines));
    }

    public double factor(String fromStopId, String toStopId) {
        Double f = factorsByEdge.get().get(fromStopId + "|" + toStopId);
        return f == null ? 1.0 : f;
    }

    public SegmentDwellHistory.Stat edgeBaseline(String fromStopId, String toStopId) {
        return edgeBaselines.get().get(fromStopId + "|" + toStopId);
    }

    public int size() {
        return factorsByEdge.get().size();
    }
}
