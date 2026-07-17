package biz.ugur.busroutebackend.transport.infrastructure.prediction.snap;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ImplausibleJumpHandler {

    private static final double IMPLAUSIBLE_JUMP_THRESHOLD = 0.25;
    private static final double SAME_REJECTED_FRAC_TOLERANCE = 0.05;
    private static final int RESET_AFTER_STRIKES = 3;

    public record Result(
            boolean plausibleSnap,
            boolean resetToDR,
            double newRejectedFrac,
            int newImplausibleCount,
            boolean resetTriggered) {

        static Result skipEvaluation(double currentRejectedFrac, int currentImplausibleCount) {
            return new Result(true, false, currentRejectedFrac, currentImplausibleCount, false);
        }

        static Result accepted() {
            return new Result(true, false, -1, 0, false);
        }

        static Result implausibleStrike(double rejectedFrac, int strikes) {
            return new Result(false, false, rejectedFrac, strikes, true);
        }

        static Result resetToDeadReckoning() {
            return new Result(true, true, -1, 0, true);
        }
    }

    public Result evaluate(VehiclePredictionState existing,
                           String vehicleId, String routeId, int direction,
                           double realFraction,
                           boolean headingCorrected, boolean fracCorrected,
                           boolean routeChanged,
                           double currentRejectedFrac, int currentImplausibleCount) {

        if (headingCorrected || fracCorrected || routeChanged
                || existing == null || existing.getLastGpsFraction() < 0) {
            return Result.skipEvaluation(currentRejectedFrac, currentImplausibleCount);
        }

        double lastGpsFrac = existing.getLastGpsFraction();
        double jumpSize = Math.abs(realFraction - lastGpsFrac);

        if (jumpSize <= IMPLAUSIBLE_JUMP_THRESHOLD) {
            return Result.accepted();
        }

        boolean sameRejectedLocation = existing.getLastRejectedGpsFraction() >= 0
                && Math.abs(realFraction - existing.getLastRejectedGpsFraction()) < SAME_REJECTED_FRAC_TOLERANCE;
        int strikes = sameRejectedLocation
                ? existing.getConsecutiveImplausibleCount() + 1
                : 1;

        if (strikes >= RESET_AFTER_STRIKES) {
            log.info("[GPS_PIPELINE] SNAP_IMPLAUSIBLE_RESET vehicle={} route={} dir={} frac={}→{} jump={} ({}x) — resetting to dead-reckoning at GPS position",
                    vehicleId, routeId, direction,
                    String.format("%.4f", lastGpsFrac),
                    String.format("%.4f", realFraction),
                    String.format("%.4f", jumpSize),
                    strikes);
            return Result.resetToDeadReckoning();
        }

        log.debug("[GPS_PIPELINE] SNAP_IMPLAUSIBLE vehicle={} route={} dir={} lastFrac={}→newFrac={} jump={} ({}/3) — keeping predicted, entering cold-start",
                vehicleId, routeId, direction,
                String.format("%.4f", lastGpsFrac),
                String.format("%.4f", realFraction),
                String.format("%.4f", jumpSize),
                strikes);
        return Result.implausibleStrike(realFraction, strikes);
    }
}
