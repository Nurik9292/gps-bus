package biz.ugur.busroutebackend.replay.metrics;

import java.util.ArrayList;
import java.util.List;

public class PositionMetrics {

    private final List<Double> absErrors = new ArrayList<>();
    private final List<Double> stepSizes = new ArrayList<>();
    private Double lastS;
    private int reanchors;
    private int teleports;
    private int modeChanges;
    private String lastMode;

    private final double teleportStepMeters;

    public PositionMetrics(double teleportStepMeters) {
        this.teleportStepMeters = teleportStepMeters;
    }

    public void add(double sEst, Double sTrue, String mode) {
        if (sTrue != null) absErrors.add(Math.abs(sEst - sTrue));
        if (lastS != null) {
            double step = Math.abs(sEst - lastS);
            stepSizes.add(step);
            if (step > teleportStepMeters) teleports++;
        }
        if (lastMode != null && !lastMode.equals(mode)) modeChanges++;
        lastS = sEst;
        lastMode = mode;
    }

    public void markReanchor() {
        reanchors++;
    }

    public double maxAbsError() {
        return absErrors.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
    }

    public double meanAbsError() {
        return absErrors.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    public double p95AbsError() {
        if (absErrors.isEmpty()) return Double.NaN;
        List<Double> sorted = absErrors.stream().sorted().toList();
        return sorted.get((int) Math.min(sorted.size() - 1, Math.floor(0.95 * (sorted.size() - 1))));
    }

    public double maxStep() {
        return stepSizes.stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    public int teleports() {
        return teleports;
    }

    public int reanchors() {
        return reanchors;
    }

    public int modeChanges() {
        return modeChanges;
    }
}
