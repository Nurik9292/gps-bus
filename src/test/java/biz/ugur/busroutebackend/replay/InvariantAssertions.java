package biz.ugur.busroutebackend.replay;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public final class InvariantAssertions {

    private InvariantAssertions() {}

    public static void assertInv2SWithinLine(List<ReplayHarness.Sample> samples, double totalMeters) {
        for (ReplayHarness.Sample s : samples) {
            assertThat(s.sEst())
                    .as("INV-2: s in [0,L] at t=%s", s.tSec())
                    .isBetween(0.0, totalMeters);
        }
    }

    public static void assertInv7BoundedStep(List<ReplayHarness.Sample> samples, double maxStepMeters) {
        for (int i = 1; i < samples.size(); i++) {
            double step = Math.abs(samples.get(i).sEst() - samples.get(i - 1).sEst());
            assertThat(step)
                    .as("INV-7/8: bounded step at t=%s", samples.get(i).tSec())
                    .isLessThanOrEqualTo(maxStepMeters);
        }
    }

    public static void assertInv3BoundedAbsError(List<ReplayHarness.Sample> samples, double maxAbsMeters) {
        for (ReplayHarness.Sample s : samples) {
            if (Double.isNaN(s.sTrue())) continue;
            assertThat(Math.abs(s.sEst() - s.sTrue()))
                    .as("INV-3: |s_est - s_true| bounded at t=%s", s.tSec())
                    .isLessThanOrEqualTo(maxAbsMeters);
        }
    }

    public static void assertErrorDoesNotGrowUnbounded(List<ReplayHarness.Sample> samples,
                                                       int windowSize, double allowedGrowthFactor) {
        List<ReplayHarness.Sample> withTruth = samples.stream()
                .filter(s -> !Double.isNaN(s.sTrue())).toList();
        if (withTruth.size() < windowSize * 2) return;
        double early = meanAbs(withTruth.subList(0, windowSize));
        double late = meanAbs(withTruth.subList(withTruth.size() - windowSize, withTruth.size()));
        assertThat(late)
                .as("Scenario-15 core property: accumulated |s-error| must stay bounded (early=%.1f)", early)
                .isLessThanOrEqualTo(Math.max(early, 1.0) * allowedGrowthFactor);
    }

    private static double meanAbs(List<ReplayHarness.Sample> xs) {
        return xs.stream().mapToDouble(s -> Math.abs(s.sEst() - s.sTrue())).average().orElse(0);
    }
}
