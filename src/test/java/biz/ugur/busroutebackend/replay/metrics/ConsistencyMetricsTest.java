package biz.ugur.busroutebackend.replay.metrics;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ConsistencyMetricsTest {

    @Test
    void neesOfPerfectlyCalibratedEstimatorIsNearOne() {
        Random rnd = new Random(42);
        double variance = 25.0;
        ConsistencyMetrics m = new ConsistencyMetrics();
        for (int i = 0; i < 20_000; i++) {
            double error = rnd.nextGaussian() * Math.sqrt(variance);
            m.addNees(error, variance);
        }
        assertThat(m.meanNees()).isBetween(0.95, 1.05);
        assertThat(m.neesWithinChi2Bounds(0.95)).isTrue();
    }

    @Test
    void neesOfOverconfidentEstimatorExceedsChi2Bounds() {
        Random rnd = new Random(7);
        double trueVariance = 100.0;
        double claimedVariance = 10.0;
        ConsistencyMetrics m = new ConsistencyMetrics();
        for (int i = 0; i < 5_000; i++) {
            double error = rnd.nextGaussian() * Math.sqrt(trueVariance);
            m.addNees(error, claimedVariance);
        }
        assertThat(m.meanNees()).isGreaterThan(5.0);
        assertThat(m.neesWithinChi2Bounds(0.95)).isFalse();
    }

    @Test
    void nisOfWhiteInnovationsMatchesItsCovariance() {
        Random rnd = new Random(123);
        double innovationVariance = 49.0;
        ConsistencyMetrics m = new ConsistencyMetrics();
        for (int i = 0; i < 20_000; i++) {
            double innovation = rnd.nextGaussian() * Math.sqrt(innovationVariance);
            m.addNis(innovation, innovationVariance);
        }
        assertThat(m.meanNis()).isBetween(0.95, 1.05);
    }
}
