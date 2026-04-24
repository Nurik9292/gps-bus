package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import java.time.Instant;

final class PredictionMath {

    static final int SPEED_BUFFER_SIZE = 5;

    private PredictionMath() {
    }

    static double findPreviousStopFraction(double[] sortedFractions, double currentFraction) {
        double prev = -1;
        for (double f : sortedFractions) {
            if (f >= currentFraction - 0.001) break;
            prev = f;
        }
        return prev;
    }

    static double findNextStopFraction(double[] sortedFractions, double currentFraction) {
        for (double f : sortedFractions) {
            if (f > currentFraction + 0.001) return f;
        }
        return -1;
    }

    static double[] appendSpeedToBuffer(double[] prev, double newSpeed) {
        if (prev == null || prev.length == 0) {
            return new double[]{newSpeed};
        }
        if (prev.length < SPEED_BUFFER_SIZE) {
            double[] buf = new double[prev.length + 1];
            System.arraycopy(prev, 0, buf, 0, prev.length);
            buf[prev.length] = newSpeed;
            return buf;
        }
        double[] buf = new double[SPEED_BUFFER_SIZE];
        System.arraycopy(prev, 1, buf, 0, SPEED_BUFFER_SIZE - 1);
        buf[SPEED_BUFFER_SIZE - 1] = newSpeed;
        return buf;
    }

    static double computeSmoothedSpeed(double[] prev, double newSpeed) {
        double[] buffer = appendSpeedToBuffer(prev, newSpeed);
        double sum = 0;
        for (double s : buffer) sum += s;
        return sum / buffer.length;
    }

    static final double LONG_TERM_ALPHA = 0.05;

    static double updateLongTermAvgSpeed(double prevAvg, double newSpeed) {
        if (prevAvg < 0) {
            return newSpeed;
        }
        return LONG_TERM_ALPHA * newSpeed + (1.0 - LONG_TERM_ALPHA) * prevAvg;
    }

    // 1-D Kalman filter over the bus speed.
    // State: instantaneous speed (km/h). Measurement: raw GPS speed.
    // Tuned for the city-bus scenario: ~±4 km/h GPS speed error, ~5 km/h²
    // process noise accounting for acceleration / braking between ticks.
    static final double KALMAN_PROCESS_NOISE = 5.0;
    static final double KALMAN_MEASUREMENT_NOISE = 4.0;
    static final double KALMAN_INITIAL_VARIANCE = 25.0;

    // Returns {newEstimate, newVariance}. prevEstimate < 0 means "not yet
    // initialised" — the filter bootstraps from the current measurement.
    static double[] updateKalmanSpeed(double prevEstimate, double prevVariance, double measurement) {
        if (prevEstimate < 0) {
            return new double[]{measurement, KALMAN_INITIAL_VARIANCE};
        }
        double priorVariance = prevVariance + KALMAN_PROCESS_NOISE;
        double gain = priorVariance / (priorVariance + KALMAN_MEASUREMENT_NOISE);
        double newEstimate = prevEstimate + gain * (measurement - prevEstimate);
        double newVariance = (1.0 - gain) * priorVariance;
        return new double[]{newEstimate, newVariance};
    }

    static PositionConfidence computeConfidence(Instant lastReceivedAt, double fractionOnRoute, Instant now) {
        if (lastReceivedAt == null) return PositionConfidence.STALE;
        long ageMs = now.toEpochMilli() - lastReceivedAt.toEpochMilli();
        if (ageMs <= 3_000 && fractionOnRoute >= 0) {
            return PositionConfidence.HIGH;
        }
        if (ageMs <= 10_000) {
            return PositionConfidence.MEDIUM;
        }
        if (ageMs <= 30_000) {
            return PositionConfidence.LOW;
        }
        return PositionConfidence.STALE;
    }
}
