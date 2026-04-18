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
