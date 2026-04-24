package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionMathKalmanTest {

    @Test
    void firstSampleBootstrapsFromMeasurement() {
        double[] result = PredictionMath.updateKalmanSpeed(-1, 0, 30.0);
        assertThat(result[0]).isEqualTo(30.0);
        assertThat(result[1]).isEqualTo(PredictionMath.KALMAN_INITIAL_VARIANCE);
    }

    @Test
    void steadyStreamOfSameValueConvergesToThatValue() {
        double estimate = -1;
        double variance = 0;
        for (int i = 0; i < 50; i++) {
            double[] result = PredictionMath.updateKalmanSpeed(estimate, variance, 25.0);
            estimate = result[0];
            variance = result[1];
        }
        assertThat(estimate).isCloseTo(25.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void varianceShrinksWithMoreSamples() {
        double estimate = -1;
        double variance = 0;
        double[] first = PredictionMath.updateKalmanSpeed(estimate, variance, 20.0);
        double firstVar = first[1];
        estimate = first[0];
        variance = first[1];

        for (int i = 0; i < 20; i++) {
            double[] step = PredictionMath.updateKalmanSpeed(estimate, variance, 20.0);
            estimate = step[0];
            variance = step[1];
        }
        assertThat(variance).isLessThan(firstVar);
    }

    @Test
    void noisyMeasurementsAreSmoothedTowardsTheTruth() {
        double estimate = -1;
        double variance = 0;
        double[] noisyMeasurements = {22, 28, 18, 26, 24, 21, 27, 23, 25, 22};
        for (double m : noisyMeasurements) {
            double[] r = PredictionMath.updateKalmanSpeed(estimate, variance, m);
            estimate = r[0];
            variance = r[1];
        }
        assertThat(estimate).isCloseTo(23.6, org.assertj.core.data.Offset.offset(2.0));
    }

    @Test
    void suddenJumpDoesNotImmediatelyReplaceEstimate() {
        double estimate = 30.0;
        double variance = 2.0;
        double[] r = PredictionMath.updateKalmanSpeed(estimate, variance, 0.0);
        assertThat(r[0]).isGreaterThan(10.0);
        assertThat(r[0]).isLessThan(30.0);
    }
}
