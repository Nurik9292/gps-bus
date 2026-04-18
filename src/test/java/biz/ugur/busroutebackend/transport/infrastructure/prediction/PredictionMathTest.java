package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionMathTest {

    @Nested
    class FindNextStopFraction {

        @Test
        void returnsFirstFractionGreaterThanCurrent() {
            double[] stops = {0.1, 0.3, 0.5, 0.8};
            assertThat(PredictionMath.findNextStopFraction(stops, 0.2)).isEqualTo(0.3);
            assertThat(PredictionMath.findNextStopFraction(stops, 0.4)).isEqualTo(0.5);
            assertThat(PredictionMath.findNextStopFraction(stops, 0.0)).isEqualTo(0.1);
        }

        @Test
        void returnsMinusOneWhenNoStopAhead() {
            double[] stops = {0.1, 0.3, 0.5};
            assertThat(PredictionMath.findNextStopFraction(stops, 0.9)).isEqualTo(-1);
            assertThat(PredictionMath.findNextStopFraction(stops, 0.5)).isEqualTo(-1);
        }

        @Test
        void skipsStopWithinEpsilon() {
            double[] stops = {0.2, 0.4};
            assertThat(PredictionMath.findNextStopFraction(stops, 0.2000005)).isEqualTo(0.4);
        }

        @Test
        void handlesEmptyArray() {
            assertThat(PredictionMath.findNextStopFraction(new double[0], 0.5)).isEqualTo(-1);
        }
    }

    @Nested
    class FindPreviousStopFraction {

        @Test
        void returnsLastFractionBeforeCurrent() {
            double[] stops = {0.1, 0.3, 0.5, 0.8};
            assertThat(PredictionMath.findPreviousStopFraction(stops, 0.4)).isEqualTo(0.3);
            assertThat(PredictionMath.findPreviousStopFraction(stops, 0.9)).isEqualTo(0.8);
        }

        @Test
        void returnsMinusOneWhenNoStopBefore() {
            double[] stops = {0.3, 0.5};
            assertThat(PredictionMath.findPreviousStopFraction(stops, 0.1)).isEqualTo(-1);
            assertThat(PredictionMath.findPreviousStopFraction(stops, 0.3)).isEqualTo(-1);
        }

        @Test
        void respectsEpsilonAtBoundary() {
            double[] stops = {0.2, 0.4};
            assertThat(PredictionMath.findPreviousStopFraction(stops, 0.2005)).isEqualTo(-1);
            assertThat(PredictionMath.findPreviousStopFraction(stops, 0.25)).isEqualTo(0.2);
        }

        @Test
        void handlesEmptyArray() {
            assertThat(PredictionMath.findPreviousStopFraction(new double[0], 0.5)).isEqualTo(-1);
        }
    }

    @Nested
    class AppendSpeedToBuffer {

        @Test
        void returnsSingletonForNullOrEmptyInput() {
            assertThat(PredictionMath.appendSpeedToBuffer(null, 15.0)).containsExactly(15.0);
            assertThat(PredictionMath.appendSpeedToBuffer(new double[0], 20.0)).containsExactly(20.0);
        }

        @Test
        void growsBufferUpToMaxSize() {
            double[] buf = {10, 20};
            double[] result = PredictionMath.appendSpeedToBuffer(buf, 30);
            assertThat(result).containsExactly(10, 20, 30);
        }

        @Test
        void reachesMaxSizeAtFifthElement() {
            double[] buf = {10, 20, 30, 40};
            double[] result = PredictionMath.appendSpeedToBuffer(buf, 50);
            assertThat(result).containsExactly(10, 20, 30, 40, 50);
            assertThat(result).hasSize(PredictionMath.SPEED_BUFFER_SIZE);
        }

        @Test
        void shiftsLeftWhenBufferFull() {
            double[] buf = {10, 20, 30, 40, 50};
            double[] result = PredictionMath.appendSpeedToBuffer(buf, 60);
            assertThat(result).containsExactly(20, 30, 40, 50, 60);
            assertThat(result).hasSize(PredictionMath.SPEED_BUFFER_SIZE);
        }

        @Test
        void doesNotMutateInputBuffer() {
            double[] buf = {10, 20, 30, 40, 50};
            PredictionMath.appendSpeedToBuffer(buf, 99);
            assertThat(buf).containsExactly(10, 20, 30, 40, 50);
        }
    }

    @Nested
    class ComputeSmoothedSpeed {

        @Test
        void returnsNewSpeedForEmptyHistory() {
            assertThat(PredictionMath.computeSmoothedSpeed(null, 40.0)).isEqualTo(40.0);
            assertThat(PredictionMath.computeSmoothedSpeed(new double[0], 25.0)).isEqualTo(25.0);
        }

        @Test
        void averagesBufferPlusNewSpeed() {
            double[] buf = {10, 20, 30, 40};
            assertThat(PredictionMath.computeSmoothedSpeed(buf, 50)).isEqualTo(30.0);
        }

        @Test
        void averagesOverSlidingWindow() {
            double[] buf = {10, 20, 30, 40, 50};
            assertThat(PredictionMath.computeSmoothedSpeed(buf, 60)).isEqualTo(40.0);
        }
    }

    @Nested
    class ComputeConfidence {

        private final Instant now = Instant.parse("2026-04-18T12:00:00Z");

        @Test
        void returnsStaleWhenLastReceivedIsNull() {
            assertThat(PredictionMath.computeConfidence(null, 0.5, now))
                    .isEqualTo(PositionConfidence.STALE);
        }

        @Test
        void returnsHighWhenRecentAndOnRoute() {
            Instant recent = now.minusMillis(2_000);
            assertThat(PredictionMath.computeConfidence(recent, 0.5, now))
                    .isEqualTo(PositionConfidence.HIGH);
        }

        @Test
        void downgradesToMediumWhenNotOnRoute() {
            Instant recent = now.minusMillis(2_000);
            assertThat(PredictionMath.computeConfidence(recent, -1, now))
                    .isEqualTo(PositionConfidence.MEDIUM);
        }

        @Test
        void returnsMediumForMidRangeAge() {
            Instant recent = now.minusMillis(8_000);
            assertThat(PredictionMath.computeConfidence(recent, 0.5, now))
                    .isEqualTo(PositionConfidence.MEDIUM);
        }

        @Test
        void returnsLowForOlderState() {
            Instant recent = now.minusMillis(20_000);
            assertThat(PredictionMath.computeConfidence(recent, 0.5, now))
                    .isEqualTo(PositionConfidence.LOW);
        }

        @Test
        void returnsStaleWhenAgeAboveThreshold() {
            Instant recent = now.minusMillis(60_000);
            assertThat(PredictionMath.computeConfidence(recent, 0.5, now))
                    .isEqualTo(PositionConfidence.STALE);
        }
    }
}
