package biz.ugur.busroutebackend.replay.metrics;

import java.util.ArrayList;
import java.util.List;

public class ConsistencyMetrics {

    private final List<Double> nees = new ArrayList<>();
    private final List<Double> nis = new ArrayList<>();

    public void addNees(double error, double claimedVariance) {
        if (claimedVariance <= 0) throw new IllegalArgumentException("variance must be > 0");
        nees.add(error * error / claimedVariance);
    }

    public void addNis(double innovation, double innovationVariance) {
        if (innovationVariance <= 0) throw new IllegalArgumentException("variance must be > 0");
        nis.add(innovation * innovation / innovationVariance);
    }

    public double meanNees() {
        return nees.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    public double meanNis() {
        return nis.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    public int neesCount() {
        return nees.size();
    }

    public boolean neesWithinChi2Bounds(double confidence) {
        return meanWithinChi2Bounds(meanNees(), nees.size(), confidence);
    }

    public boolean nisWithinChi2Bounds(double confidence) {
        return meanWithinChi2Bounds(meanNis(), nis.size(), confidence);
    }

    static boolean meanWithinChi2Bounds(double mean, int n, double confidence) {
        if (n == 0) return false;
        double z = confidence >= 0.99 ? 2.576 : 1.96;
        double half = z * Math.sqrt(2.0 / n);
        return mean >= 1.0 - half && mean <= 1.0 + half;
    }
}
