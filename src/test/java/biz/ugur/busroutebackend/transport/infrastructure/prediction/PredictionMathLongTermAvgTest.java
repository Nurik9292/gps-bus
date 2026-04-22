package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionMathLongTermAvgTest {

    @Test
    void firstSampleBecomesAverage() {
        double avg = PredictionMath.updateLongTermAvgSpeed(-1, 42.0);
        assertThat(avg).isEqualTo(42.0);
    }

    @Test
    void exponentialSmoothingFollowsWithLag() {
        double avg = 40.0;
        for (int i = 0; i < 3; i++) {
            avg = PredictionMath.updateLongTermAvgSpeed(avg, 10.0);
        }
        assertThat(avg).isGreaterThan(35.0).isLessThan(40.0);
    }

    @Test
    void convergesToSteadyInputAfterManyUpdates() {
        double avg = 0.0;
        for (int i = 0; i < 200; i++) {
            avg = PredictionMath.updateLongTermAvgSpeed(avg, 25.0);
        }
        assertThat(avg).isCloseTo(25.0, org.assertj.core.data.Offset.offset(0.5));
    }

    @Test
    void suddenStopDoesNotImmediatelyZeroTheAverage() {
        double avg = 30.0;
        avg = PredictionMath.updateLongTermAvgSpeed(avg, 0.0);
        assertThat(avg).isGreaterThan(20.0);
    }
}
